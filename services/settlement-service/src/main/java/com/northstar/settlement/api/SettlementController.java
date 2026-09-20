package com.northstar.settlement.api;

import com.northstar.settlement.domain.CalculatedSettlement;
import com.northstar.settlement.domain.LegacyCoercions;
import com.northstar.settlement.domain.LegacyDisplay;
import com.northstar.settlement.domain.SettlementRequest;
import com.northstar.settlement.domain.SettlementResult;
import com.northstar.settlement.domain.SettlementService;
import com.northstar.settlement.persistence.ClaimRecord;
import com.northstar.settlement.persistence.SettlementRow;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three {@code /settlement/*.do} mappings from {@code struts-config.xml}
 * (SETTLE-R01, SETTLE-R02, SETTLE-R03) plus a read-only claim lookup used by
 * the parity probes. See docs/STRUTS_TO_SPRING_MAPPING.md.
 *
 * <p>Parameters are bound as raw strings, optional, and never validated by the
 * framework (SETTLE-R04, SETTLE-R05, SETTLE-R19): the coercions live in
 * {@link SettlementRequest} and {@link LegacyCoercions}. The one validation this
 * module performs is the CHG-001 deductible check on calculate (SETTLE-R18 v2).
 */
@RestController
@RequestMapping("/api/settlement")
public class SettlementController {

    private final SettlementService service;

    public SettlementController(SettlementService service) {
        this.service = service;
    }

    /**
     * {@code POST /settlement/calculate.do} (SETTLE-R01).
     *
     * <p>SETTLE-R18 v2 (CHG-001, approved future state): a non-blank deductible that is
     * not a valid number is rejected before any calculation and the calculate screen is
     * redisplayed with {@code settlement.deductible.invalid}, HTTP 200, empty fields
     * (OQ-15 c) and no result. Blank stays 0.00 (SETTLE-R16, SETTLE-R17). The save
     * route is out of CHG-001's scope and keeps SETTLE-R18 (OQ-15 a).
     */
    @PostMapping("/calculate")
    public ScreenResponse<CalculatedSettlement> calculate(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation,
            @RequestParam(required = false) String policyLimit) {
        if (LegacyCoercions.isInvalidDeductible(deductible)) {
            return ScreenResponse.invalid(ScreenResponse.CALCULATE_JSP, ScreenResponse.DEDUCTIBLE_INVALID);
        }
        CalculatedSettlement calculated = service.calculate(
                new SettlementRequest(claimId, coveredAmount, deductible, depreciation, policyLimit));
        return ScreenResponse.of(ScreenResponse.CALCULATE_JSP, calculateFields(calculated.result()), calculated);
    }

    /**
     * {@code GET /settlement/calculate.do?claimId=}. Struts action mappings answer any
     * HTTP method (SETTLE-R01) and the transcript probe {@code settlement.claim.<id>.amount}
     * is a GET with only {@code claimId}, so the same recalculation is reachable by GET.
     */
    @GetMapping("/calculate")
    public ScreenResponse<CalculatedSettlement> calculateByGet(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation,
            @RequestParam(required = false) String policyLimit) {
        return calculate(claimId, coveredAmount, deductible, depreciation, policyLimit);
    }

    /** {@code POST /settlement/save.do}. */
    @PostMapping("/save")
    public ScreenResponse<SettlementRow> save(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation,
            @RequestParam(required = false) String policyLimit) {
        SettlementRow saved = service.save(
                new SettlementRequest(claimId, coveredAmount, deductible, depreciation, policyLimit));
        // SETTLE-R08: save.jsp shows the rounded amount and the session user, not the row.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("settlementAmount", LegacyDisplay.money(saved.settlementAmount()));
        fields.put("savedBy", service.operator());
        return ScreenResponse.of(ScreenResponse.SAVE_JSP, fields, saved);
    }

    /** {@code GET /settlement/detail.do?claimId=}. */
    @GetMapping("/detail")
    public ResponseEntity<ScreenResponse<SettlementRow>> detail(
            @RequestParam(required = false) String claimId) {
        // SETTLE-R11: the detail action applies the same claim 119 fallback.
        int resolved = LegacyCoercions.integer(claimId, LegacyCoercions.DEFAULT_CLAIM_ID);
        return service.detail(resolved)
                .map(row -> ResponseEntity.ok(ScreenResponse.of(ScreenResponse.DETAIL_JSP, detailFields(row), row)))
                // SETTLE-R46: the monolith forwarded a null bean and detail.jsp blew up;
                // the service reports the missing settlement instead (see KNOWN_LEGACY_QUIRKS).
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Parity probe support: the claim as stored, to prove calculate leaves it untouched (SETTLE-R44). */
    @GetMapping("/claims/{claimId}")
    public ResponseEntity<ScreenResponse<ClaimRecord>> claim(@PathVariable int claimId) {
        return service.claim(claimId)
                .map(claim -> {
                    Map<String, String> fields = new LinkedHashMap<>();
                    fields.put("claimStatus", claim.status());
                    return ResponseEntity.ok(ScreenResponse.of("claim", fields, claim));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** SETTLE-R07: the five {@code f_*} spans of calculate.jsp, in page order. */
    private static Map<String, String> calculateFields(SettlementResult result) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("coveredAmount", LegacyDisplay.money(result.coveredAmount()));
        fields.put("deductibleApplied", LegacyDisplay.money(result.deductibleApplied()));
        fields.put("depreciation", LegacyDisplay.money(result.depreciation()));
        fields.put("cappedAtLimit", LegacyDisplay.flag(result.cappedAtLimit()));       // SETTLE-R35
        fields.put("settlementAmount", LegacyDisplay.money(result.settlementAmount()));
        return fields;
    }

    /** The {@code f_detail*} spans of detail.jsp, in page order. */
    private static Map<String, String> detailFields(SettlementRow row) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("detailSettlementId", LegacyDisplay.integer(row.settlementId()));
        fields.put("detailClaimId", LegacyDisplay.integer(row.claimId()));
        fields.put("detailCoveredAmount", LegacyDisplay.money(row.coveredAmount()));
        fields.put("detailDeductible", LegacyDisplay.money(row.deductibleApplied()));
        fields.put("detailDepreciation", LegacyDisplay.money(row.depreciation()));
        fields.put("detailCapped", LegacyDisplay.flag(row.cappedAtLimit()));
        fields.put("detailAmount", LegacyDisplay.money(row.settlementAmount()));
        fields.put("detailDate", row.calculatedDate().toString());
        return fields;
    }
}
