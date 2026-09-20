package com.northstar.settlement.calc;

import org.springframework.stereotype.Component;

/**
 * Settlement arithmetic of SPEC-SETTLE-001 section 3.3, ported from
 * {@code com.northstar.claims.service.SettlementCalculator} without behavioural change.
 *
 * <p>The double arithmetic is deliberate: SETTLE-R28 and SETTLE-R29 depend on
 * {@code Math.round(amount * 100.0) / 100.0} over binary doubles, so a move to
 * {@code BigDecimal} here would change results (OQ-05).
 */
@Component
public class SettlementCalculator {

    /** Applies depreciation, deductible, policy cap and cent rounding (SETTLE-R21 to SETTLE-R33). */
    public Settlement calculate(double coveredAmount, String deductible, double depreciation, double policyLimit) {
        // SETTLE-R16, SETTLE-R17: absent, empty or whitespace-only deductible is 0.00.
        // SETTLE-R18: any other non-numeric value throws and reaches the legacy error page.
        double deductibleValue = 0;
        if (deductible != null && !deductible.trim().isEmpty()) {
            deductibleValue = Double.parseDouble(deductible);
        }
        double gross = coveredAmount - depreciation;                 // SETTLE-R21
        double afterDeductible = gross - deductibleValue;            // SETTLE-R22
        if (afterDeductible < 0) {                                   // SETTLE-R23
            afterDeductible = 0;
        }
        boolean capped = afterDeductible > policyLimit;              // SETTLE-R24, R25, R26, R27
        double amount = capped ? policyLimit : afterDeductible;
        double rounded = Math.round(amount * 100.0) / 100.0;         // SETTLE-R28, R32
        // SETTLE-R30, SETTLE-R31: the deductible is echoed in full and the inputs unrounded.
        return new Settlement(null, null, coveredAmount, deductibleValue, depreciation, capped, rounded, null, null);
    }
}
