package com.northstar.settlement.domain;

import java.math.BigDecimal;
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

    public SettlementResult calculate(BigDecimal coveredAmount,
            BigDecimal deductible, BigDecimal depreciation,
            BigDecimal policyLimit) {
        double gross = coveredAmount.doubleValue() - depreciation.doubleValue();
        double afterDeductible = gross - deductible.doubleValue();
        if (afterDeductible < 0) {
            afterDeductible = 0;
        }
        double limit = policyLimit.doubleValue();
        boolean capped = afterDeductible > limit;
        double amount = capped ? limit : afterDeductible;
        double rounded = Math.round(amount * 100.0) / 100.0;
        return new SettlementResult(coveredAmount, deductible, depreciation,
                capped, BigDecimal.valueOf(rounded));
    }
}
