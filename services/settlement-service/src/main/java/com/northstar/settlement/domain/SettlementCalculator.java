package com.northstar.settlement.domain;

import org.springframework.stereotype.Component;

/**
 * SETTLE-R07, SETTLE-R08, SETTLE-R09 v2. Line for line the arithmetic of the
 * legacy com.northstar.claims.service.SettlementCalculator, on {@code double}:
 * {@code Math.round(amount * 100.0) / 100.0} is not half-up rounding of the
 * decimal value (QUIRK-08, transcripts/settlement_half_cent.json).
 */
@Component
public class SettlementCalculator {

    public SettlementResult calculate(double coveredAmount, double deductible,
            double depreciation, double policyLimit) {
        double gross = coveredAmount - depreciation;
        double afterDeductible = gross - deductible;
        if (afterDeductible < 0) {
            afterDeductible = 0;
        }
        boolean capped = afterDeductible > policyLimit;
        double amount = capped ? policyLimit : afterDeductible;
        double rounded = Math.round(amount * 100.0) / 100.0;
        return new SettlementResult(coveredAmount, deductible, depreciation,
                capped, rounded);
    }
}
