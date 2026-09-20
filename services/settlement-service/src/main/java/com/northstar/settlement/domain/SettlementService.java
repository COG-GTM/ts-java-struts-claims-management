package com.northstar.settlement.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.northstar.settlement.persistence.ClaimRepository;
import com.northstar.settlement.persistence.PolicyRepository;
import com.northstar.settlement.persistence.SettlementRepository;
import com.northstar.settlement.persistence.SettlementRow;

/** The request-to-rules layer of SettlementCalculateAction and SettlementSaveAction. */
@Service
public class SettlementService {

    static final int DEFAULT_CLAIM_ID = 119;
    static final double DEFAULT_COVERED = 5000;
    static final double DEFAULT_DEPRECIATION = 0;
    static final double FALLBACK_LIMIT = 10000;
    static final String SAVE_DATE = "2019-04-01";

    private final SettlementCalculator calculator;
    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final SettlementRepository settlements;
    private final String operator;

    public SettlementService(SettlementCalculator calculator,
            ClaimRepository claims, PolicyRepository policies,
            SettlementRepository settlements,
            @Value("${northstar.operator}") String operator) {
        this.calculator = calculator;
        this.claims = claims;
        this.policies = policies;
        this.settlements = settlements;
        this.operator = operator;
    }

    /** SETTLE-R02 to R09. Missing claim or policy falls back to 10000 (R05). */
    public SettlementResult calculate(String claimId, String coveredAmount,
            String deductible, String depreciation) {
        int id = LegacyCoercions.integer(claimId, DEFAULT_CLAIM_ID);
        double limit = claims.findPolicyId(id)
                .flatMap(policies::findLimit)
                .orElse(FALLBACK_LIMIT);
        return compute(coveredAmount, deductible, depreciation, limit);
    }

    /**
     * SETTLE-R12. Unlike calculate, the legacy save action dereferences the
     * claim and policy without a null check, so a missing claim is a system
     * error rather than a fallback.
     */
    public SettlementRow save(String claimId, String coveredAmount,
            String deductible, String depreciation) {
        int id = LegacyCoercions.integer(claimId, DEFAULT_CLAIM_ID);
        int policyId = claims.findPolicyId(id).orElseThrow(
                () -> new ClaimNotFoundException(id));
        double limit = policies.findLimit(policyId).orElseThrow(
                () -> new ClaimNotFoundException(id));
        SettlementResult result = compute(coveredAmount, deductible,
                depreciation, limit);
        return settlements.insertNext(id, result.coveredAmount(),
                result.deductibleApplied(), result.depreciation(),
                result.cappedAtLimit(), result.settlementAmount(), operator,
                SAVE_DATE);
    }

    /** SETTLE-R13. */
    public SettlementRow detail(String claimId) {
        int id = LegacyCoercions.integer(claimId, DEFAULT_CLAIM_ID);
        return settlements.findLatest(id).orElse(null);
    }

    private SettlementResult compute(String coveredAmount, String deductible,
            String depreciation, double limit) {
        double covered = LegacyCoercions.decimal(coveredAmount,
                DEFAULT_COVERED);
        double depreciationValue = LegacyCoercions.decimal(depreciation,
                DEFAULT_DEPRECIATION);
        double deductibleValue;
        try {
            deductibleValue = LegacyCoercions.deductible(deductible);
        } catch (NumberFormatException failure) {
            throw new InvalidDeductibleException(deductible);
        }
        return calculator.calculate(covered, deductibleValue,
                depreciationValue, limit);
    }
}
