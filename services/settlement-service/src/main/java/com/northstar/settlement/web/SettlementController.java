package com.northstar.settlement.web;

import com.northstar.settlement.calc.Settlement;
import com.northstar.settlement.calc.SettlementCalculator;
import com.northstar.settlement.persistence.ClaimRepository;
import com.northstar.settlement.persistence.PolicyRepository;
import com.northstar.settlement.persistence.SettlementRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three settlement routes of SPEC-SETTLE-001 section 3.1, extracted from
 * {@code SettlementCalculateAction}, {@code SettlementSaveAction} and
 * {@code SettlementDetailAction}.
 *
 * <p>Each route answers with the {@code ns:view} marker of the forward the legacy action
 * declared, so a replay can compare the forward as well as the fields
 * (SETTLE-R01, SETTLE-R02, SETTLE-R03).
 */
@RestController
@RequestMapping(path = "/claims/settlement", produces = MediaType.TEXT_HTML_VALUE)
public class SettlementController {

    static final String CALCULATE_VIEW = "/WEB-INF/jsp/settlement/calculate.jsp";
    static final String SAVE_VIEW = "/WEB-INF/jsp/settlement/save.jsp";
    static final String DETAIL_VIEW = "/WEB-INF/jsp/settlement/detail.jsp";

    /** The session operator the legacy stack seeds; settlements record it verbatim (SETTLE-R40). */
    static final String OPERATOR = "supervisor";

    /** The hard-coded calculation date of {@code SettlementSaveAction} (SETTLE-R41). */
    static final String CALCULATED_DATE = "2019-04-01";

    private final SettlementCalculator calculator;
    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final SettlementRepository settlements;

    public SettlementController(SettlementCalculator calculator, ClaimRepository claims,
            PolicyRepository policies, SettlementRepository settlements) {
        this.calculator = calculator;
        this.claims = claims;
        this.policies = policies;
        this.settlements = settlements;
    }

    /**
     * {@code /claims/settlement/calculate.do}: calculates and renders without persisting
     * (SETTLE-R01, SETTLE-R37).
     */
    @PostMapping(path = {"/calculate.do", "/calculate"})
    public ResponseEntity<String> calculate(
            @RequestParam(name = "claimId", required = false) String claimIdParam,
            @RequestParam(name = "coveredAmount", required = false) String coveredParam,
            @RequestParam(name = "deductible", required = false) String deductibleParam,
            @RequestParam(name = "depreciation", required = false) String depreciationParam) {

        int claimId = LegacyInputs.integer(claimIdParam, LegacyInputs.DEFAULT_CLAIM_ID);
        // SETTLE-R49: an unknown claim or policy falls back to a 10000.00 limit rather than failing.
        double limit = LegacyInputs.FALLBACK_POLICY_LIMIT;
        Integer policyId = claims.findPolicyId(claimId);
        if (policyId != null) {
            Double policyLimit = policies.findPolicyLimit(policyId);
            if (policyLimit != null) {
                limit = policyLimit;
            }
        }
        double covered = LegacyInputs.decimal(coveredParam, LegacyInputs.DEFAULT_COVERED_AMOUNT);
        double depreciation = LegacyInputs.decimal(depreciationParam, LegacyInputs.DEFAULT_DEPRECIATION);

        // SETTLE-R18 v2 (CHG-001): a non-blank, non-numeric deductible is rejected before any
        // calculation and the calculate screen is redisplayed with HTTP 200 and the validation
        // key, instead of the generic error page of SETTLE-R18. Whitespace-only stays blank
        // (SETTLE-R17), and the redisplayed spans are empty because no settlement exists
        // (OQ-15(c), the conservative reading of SETTLE-R07).
        if (isInvalidDeductible(deductibleParam)) {
            return ResponseEntity.ok(new ScreenRenderer(CALCULATE_VIEW)
                    .error("settlement.deductible.invalid")
                    .field("coveredAmount", "")
                    .field("deductibleApplied", "")
                    .field("depreciation", "")
                    .field("cappedAtLimit", "")
                    .field("settlementAmount", "")
                    .render());
        }

        Settlement settlement = calculator.calculate(
                covered, LegacyInputs.deductibleOrZero(deductibleParam), depreciation, limit);

        // SETTLE-R07: the calculate screen renders exactly these five fields, in this order.
        String body = new ScreenRenderer(CALCULATE_VIEW)
                .field("coveredAmount", ScreenRenderer.money(settlement.coveredAmount()))
                .field("deductibleApplied", ScreenRenderer.money(settlement.deductibleApplied()))
                .field("depreciation", ScreenRenderer.money(settlement.depreciation()))
                .field("cappedAtLimit", ScreenRenderer.moneyOrText(String.valueOf(settlement.cappedAtLimit())))
                .field("settlementAmount", ScreenRenderer.money(settlement.settlementAmount()))
                .render();
        return ResponseEntity.ok(body);
    }

    /** Non-blank and not parseable by {@code Double.parseDouble} (SETTLE-R17, SETTLE-R18 v2). */
    private static boolean isInvalidDeductible(String deductible) {
        if (deductible == null || deductible.trim().isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(deductible);
            return false;
        } catch (NumberFormatException invalid) {
            return true;
        }
    }

    /**
     * {@code /claims/settlement/save.do}: recalculates from the request and inserts the result
     * (SETTLE-R02, SETTLE-R38, SETTLE-R50).
     */
    @PostMapping(path = {"/save.do", "/save"})
    public ResponseEntity<String> save(
            @RequestParam(name = "claimId", required = false) String claimIdParam,
            @RequestParam(name = "coveredAmount", required = false) String coveredParam,
            @RequestParam(name = "deductible", required = false) String deductibleParam,
            @RequestParam(name = "depreciation", required = false) String depreciationParam) {

        int claimId = LegacyInputs.integer(claimIdParam, LegacyInputs.DEFAULT_CLAIM_ID);
        // OQ-15(a): CHG-001 decides the calculate route only, so save keeps the SETTLE-R18
        // behaviour and an invalid deductible reaches the generic error page through
        // LegacyFailureAdvice.
        // SETTLE-R15: save dereferences the claim and the policy unguarded, so an unknown claim
        // fails the request instead of falling back to a default limit as calculate does.
        Integer policyId = claims.findPolicyId(claimId);
        if (policyId == null) {
            throw new MissingRecordException("claim " + claimId + " has no policy");
        }
        Double limit = policies.findPolicyLimit(policyId);
        if (limit == null) {
            throw new MissingRecordException("policy " + policyId + " not found");
        }
        double covered = LegacyInputs.decimal(coveredParam, LegacyInputs.DEFAULT_COVERED_AMOUNT);
        double depreciation = LegacyInputs.decimal(depreciationParam, LegacyInputs.DEFAULT_DEPRECIATION);
        Settlement calculated = calculator.calculate(
                covered, LegacyInputs.deductibleOrZero(deductibleParam), depreciation, limit);

        // SETTLE-R39: the id comes from max(settlement_id)+1; SETTLE-R51: the claim row is untouched.
        Settlement saved = calculated.withSaveMetadata(settlements.nextId(), claimId, OPERATOR, CALCULATED_DATE);
        settlements.insert(saved);

        // SETTLE-R09: the save screen renders only the amount and the operator.
        String body = new ScreenRenderer(SAVE_VIEW)
                .field("settlementAmount", ScreenRenderer.money(saved.settlementAmount()))
                .field("savedBy", saved.calculatedBy())
                .render();
        return ResponseEntity.ok(body);
    }

    /**
     * {@code /claims/settlement/detail.do}: read-only view of the latest settlement of a claim
     * (SETTLE-R03, SETTLE-R45).
     */
    @PostMapping(path = {"/detail.do", "/detail"})
    public ResponseEntity<String> detail(
            @RequestParam(name = "claimId", required = false) String claimIdParam) {

        int claimId = LegacyInputs.integer(claimIdParam, LegacyInputs.DEFAULT_CLAIM_ID);
        Settlement settlement = settlements.findLatestByClaim(claimId);
        ScreenRenderer screen = new ScreenRenderer(DETAIL_VIEW);
        if (settlement == null) {
            // SETTLE-R46: the detail screen renders empty values when no settlement exists.
            return ResponseEntity.ok(screen.render());
        }
        String body = screen
                .field("detailSettlementId", ScreenRenderer.integer(settlement.settlementId()))
                .field("detailClaimId", ScreenRenderer.integer(settlement.claimId()))
                .field("detailCoveredAmount", ScreenRenderer.money(settlement.coveredAmount()))
                .field("detailDeductible", ScreenRenderer.money(settlement.deductibleApplied()))
                .field("detailDepreciation", ScreenRenderer.money(settlement.depreciation()))
                // SETTLE-R35: the detail screen renders the cap flag as text, not as money.
                .field("detailCapped", String.valueOf(settlement.cappedAtLimit()))
                .field("detailAmount", ScreenRenderer.money(settlement.settlementAmount()))
                .field("detailDate", ScreenRenderer.date(settlement.calculatedDate()))
                .render();
        return ResponseEntity.ok(body);
    }
}
