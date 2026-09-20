package com.northstar.settlement.domain;

/**
 * Port of {@code com.northstar.claims.service.SettlementCalculator#calculate}
 * (SPEC-SETTLE-001 section 3.3). Every step is legacy-faithful and kept in binary
 * {@code double} on purpose: rewriting this with {@code BigDecimal} changes the
 * half-cent outcome the transcripts record (SETTLE-R28).
 */
public final class SettlementCalculator {

    private SettlementCalculator() {
    }

    /**
     * Applies depreciation, deductible, policy cap and cent rounding in the legacy order.
     *
     * @param coveredAmount resolved covered amount (SETTLE-R12)
     * @param deductible    raw deductible string; blank means 0 (SETTLE-R16, SETTLE-R17),
     *                      non-numeric throws {@link NumberFormatException} (SETTLE-R18;
     *                      only reachable from save since CHG-001, SETTLE-R18 v2)
     * @param depreciation  resolved depreciation (SETTLE-R13)
     * @param policyLimit   resolved policy limit (SETTLE-R14)
     */
    public static SettlementResult calculate(double coveredAmount, String deductible,
            double depreciation, double policyLimit) {
        // SETTLE-R16/R17: blank or whitespace-only deductible is 0; SETTLE-R18: anything
        // else is parsed with no fallback and propagates NumberFormatException (the
        // calculate route rejects it first, SETTLE-R18 v2).
        double deductibleValue = 0;
        if (deductible != null && deductible.trim().length() > 0) {
            deductibleValue = Double.parseDouble(deductible);
        }
        // SETTLE-R21: gross loss.
        double gross = coveredAmount - depreciation;
        // SETTLE-R22: subtract the deductible.
        double afterDeductible = gross - deductibleValue;
        // SETTLE-R23: floor at zero, never negative.
        if (afterDeductible < 0) {
            afterDeductible = 0;
        }
        // SETTLE-R24/R25/R26/R27: strict > against the limit; equal is not capped.
        boolean capped = afterDeductible > policyLimit;
        double amount = capped ? policyLimit : afterDeductible;
        // SETTLE-R28/R32: single binary-double half-up rounding after cap and floor.
        // Legacy-faithful: 1.005 rounds to 1.00 because the double is a hair below 1.005.
        double rounded = Math.round(amount * 100.0) / 100.0;
        // SETTLE-R30/R31: echo the raw inputs and the full deductible, rounded amount only.
        return new SettlementResult(coveredAmount, deductibleValue, depreciation, capped, rounded);
    }
}
