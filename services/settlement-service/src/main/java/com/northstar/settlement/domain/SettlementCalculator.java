package com.northstar.settlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** SETTLE-R07, SETTLE-R08, SETTLE-R09. */
@Component
public class SettlementCalculator {

    public SettlementResult calculate(BigDecimal coveredAmount,
            BigDecimal deductible, BigDecimal depreciation,
            BigDecimal policyLimit) {
        BigDecimal afterDeductible = coveredAmount.subtract(depreciation)
                .subtract(deductible);
        if (afterDeductible.signum() < 0) {
            afterDeductible = BigDecimal.ZERO;
        }
        boolean capped = afterDeductible.compareTo(policyLimit) > 0;
        BigDecimal amount = capped ? policyLimit : afterDeductible;
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        return new SettlementResult(coveredAmount, deductible, depreciation,
                capped, rounded);
    }
}
