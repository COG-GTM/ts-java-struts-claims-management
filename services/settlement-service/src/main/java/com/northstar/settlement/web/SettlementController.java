package com.northstar.settlement.web;

import com.northstar.settlement.domain.CalculatedSettlement;
import com.northstar.settlement.domain.SettlementCalculator;
import com.northstar.settlement.persistence.ClaimPolicyRepository;
import com.northstar.settlement.persistence.SettlementRepository;
import com.northstar.settlement.persistence.StoredSettlement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three settlement request flows of the monolith, with the same request
 * parameter names as the Struts screens and the field names of the
 * {@code <span id="f_...">} values on calculate.jsp, save.jsp and detail.jsp.
 *
 * <p>Implements SPEC-SETTLE-001:
 * <ul>
 *   <li>SETTLE-R01 — {@code /settlement/calculate} takes claimId,
 *       coveredAmount, deductible and depreciation and answers with the
 *       calculate screen
 *       (src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:20-43).</li>
 *   <li>SETTLE-R02, R03, R04, R05 — parameter coercion, delegated to
 *       {@link LegacyParameters}.</li>
 *   <li>SETTLE-R07 — the limit is the claim's policy's {@code policy_limit}
 *       (SettlementCalculateAction.java:24-31).</li>
 *   <li>SETTLE-R08 (v2) — on the calculate path a missing claim, or a missing
 *       policy, falls back to a limit of 10000 and the request still succeeds
 *       (SettlementCalculateAction.java:25-31).</li>
 *   <li>SETTLE-R09 — no validation runs, so {@code errors} is empty
 *       (src/main/webapp/WEB-INF/struts-config.xml:206-222).</li>
 *   <li>SETTLE-R18, R23 — the calculate screen carries coveredAmount,
 *       deductibleApplied, depreciation, cappedAtLimit and settlementAmount
 *       (src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp:17-30).</li>
 *   <li>SETTLE-R24 — calculate writes nothing
 *       (SettlementCalculateAction.java:20-43).</li>
 *   <li>SETTLE-R25 — save recomputes from the same four parameters rather than
 *       reusing a calculated result
 *       (src/main/java/com/northstar/claims/web/SettlementSaveAction.java:24-33).</li>
 *   <li>SETTLE-R26 — the save path has no 10000 fallback: a missing claim or
 *       policy fails the request (SettlementSaveAction.java:25-33).</li>
 *   <li>SETTLE-R27 — the saved row takes {@code max(settlement_id)+1}, the
 *       request claimId, the operator name, and the literal calculated_date
 *       {@code 2019-04-01} (SettlementSaveAction.java:34-38).</li>
 *   <li>SETTLE-R29 — the save screen shows settlementAmount and savedBy
 *       (src/main/webapp/WEB-INF/jsp/settlement/save.jsp:19-22).</li>
 *   <li>SETTLE-R30 — detail reads the latest stored settlement for the claim
 *       and shows nothing when there is none
 *       (src/main/java/com/northstar/claims/web/SettlementDetailAction.java:17-23).</li>
 *   <li>SETTLE-R31 — the detail field set and its formatting
 *       (src/main/webapp/WEB-INF/jsp/settlement/detail.jsp:10-26).</li>
 * </ul>
 *
 * <p>Per ADR-001 ("Out of scope") the operator name is supplied input rather
 * than a session attribute: the {@code user} parameter stands in for the
 * session {@code user} attribute that SettlementSaveAction.java:36-37 reads
 * through {@code String.valueOf}, whose value for an absent attribute is the
 * string {@code null}.
 */
@RestController
public class SettlementController {

    /** SETTLE-R08 (v2): the calculate-path fallback limit. */
    static final double FALLBACK_POLICY_LIMIT = 10000;

    private final SettlementCalculator calculator;
    private final ClaimPolicyRepository claims;
    private final SettlementRepository settlements;

    public SettlementController(SettlementCalculator calculator,
            ClaimPolicyRepository claims, SettlementRepository settlements) {
        this.calculator = calculator;
        this.claims = claims;
        this.settlements = settlements;
    }

    @GetMapping("/settlement/calculate")
    public SettlementResponse calculateByGet(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation) {
        return calculate(claimId, coveredAmount, deductible, depreciation);
    }

    @PostMapping("/settlement/calculate")
    public SettlementResponse calculate(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation) {
        int claim = LegacyParameters.integer(claimId, LegacyParameters.DEFAULT_CLAIM_ID);
        double limit = policyLimit(claim).orElse(FALLBACK_POLICY_LIMIT);
        CalculatedSettlement settlement = calculator.calculate(
                LegacyParameters.decimal(coveredAmount, LegacyParameters.DEFAULT_COVERED_AMOUNT),
                LegacyParameters.deductibleOrZero(deductible),
                LegacyParameters.decimal(depreciation, LegacyParameters.DEFAULT_DEPRECIATION),
                limit);
        return SettlementResponse.of("calculate", calculateFields(settlement));
    }

    @PostMapping("/settlement/save")
    public SettlementResponse save(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation,
            @RequestParam(required = false) String user) {
        int claim = LegacyParameters.integer(claimId, LegacyParameters.DEFAULT_CLAIM_ID);
        double limit = policyLimit(claim).orElseThrow(() -> new MissingClaimException(claim));
        CalculatedSettlement settlement = calculator.calculate(
                LegacyParameters.decimal(coveredAmount, LegacyParameters.DEFAULT_COVERED_AMOUNT),
                LegacyParameters.deductibleOrZero(deductible),
                LegacyParameters.decimal(depreciation, LegacyParameters.DEFAULT_DEPRECIATION),
                limit);
        String savedBy = String.valueOf(user);
        settlements.save(new StoredSettlement(settlements.nextId(), claim,
                settlement.coveredAmount(), settlement.deductibleApplied(),
                settlement.depreciation(), settlement.cappedAtLimit(),
                settlement.settlementAmount(), savedBy, "2019-04-01"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("settlementAmount", FieldFormatter.money(settlement.settlementAmount()));
        fields.put("savedBy", FieldFormatter.text(savedBy));
        return SettlementResponse.of("save", fields);
    }

    @GetMapping("/settlement/detail")
    public SettlementResponse detail(@RequestParam(required = false) String claimId) {
        int claim = LegacyParameters.integer(claimId, LegacyParameters.DEFAULT_CLAIM_ID);
        Optional<StoredSettlement> stored = settlements.findLatestByClaim(claim);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("detailSettlementId", stored.map(row -> FieldFormatter.integer(row.settlementId())).orElse(""));
        fields.put("detailClaimId", stored.map(row -> FieldFormatter.integer(row.claimId())).orElse(""));
        fields.put("detailCoveredAmount", stored.map(row -> FieldFormatter.money(row.coveredAmount())).orElse(""));
        fields.put("detailDeductible", stored.map(row -> FieldFormatter.money(row.deductibleApplied())).orElse(""));
        fields.put("detailDepreciation", stored.map(row -> FieldFormatter.money(row.depreciation())).orElse(""));
        fields.put("detailCapped", stored.map(row -> FieldFormatter.text(String.valueOf(row.cappedAtLimit()))).orElse(""));
        fields.put("detailAmount", stored.map(row -> FieldFormatter.money(row.settlementAmount())).orElse(""));
        fields.put("detailDate", stored.map(row -> FieldFormatter.date(row.calculatedDate())).orElse(""));
        return SettlementResponse.of("detail", fields);
    }

    /** SETTLE-R07: claim to policy to policy_limit, empty when either row is missing. */
    private Optional<Double> policyLimit(int claimId) {
        return claims.findPolicyId(claimId).flatMap(claims::findPolicyLimit);
    }

    /** SETTLE-R20, R22, R23: the five calculate-screen fields as the JSP writes them. */
    private Map<String, String> calculateFields(CalculatedSettlement settlement) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("coveredAmount", FieldFormatter.money(settlement.coveredAmount()));
        fields.put("deductibleApplied", FieldFormatter.money(settlement.deductibleApplied()));
        fields.put("depreciation", FieldFormatter.money(settlement.depreciation()));
        fields.put("cappedAtLimit", FieldFormatter.money(settlement.cappedAtLimit()));
        fields.put("settlementAmount", FieldFormatter.money(settlement.settlementAmount()));
        return fields;
    }
}
