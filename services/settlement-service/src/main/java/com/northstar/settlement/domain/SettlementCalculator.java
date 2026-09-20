package com.northstar.settlement.domain;

import org.springframework.stereotype.Component;

/**
 * SETTLE-R07, SETTLE-R08, SETTLE-R09. The arithmetic runs on {@code double}
 * and rounds with {@code Math.round(amount * 100.0) / 100.0}, the same
 * statements as the legacy {@code SettlementCalculator}, so that inputs such
 * as {@code 1.005} settle at {@code 1.00} exactly as the Struts screen does
 * (docs/KNOWN_LEGACY_QUIRKS.md, QUIRK-01).
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
