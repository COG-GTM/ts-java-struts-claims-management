package com.northstar.settlement.web;

/**
 * The parameter parsing of {@code ClaimsActionSupport}: every settlement input except the
 * deductible falls back silently instead of failing (SETTLE-R11, SETTLE-R12, SETTLE-R13).
 *
 * <p>No range or sign check is applied, as in the legacy actions (SETTLE-R19).
 */
public final class LegacyInputs {

    /** Fallback claim used by all three settlement routes (SETTLE-R11, OQ-01). */
    public static final int DEFAULT_CLAIM_ID = 119;

    /** Fallback covered amount (SETTLE-R12, OQ-02). */
    public static final double DEFAULT_COVERED_AMOUNT = 5000;

    /** Fallback depreciation (SETTLE-R13). */
    public static final double DEFAULT_DEPRECIATION = 0;

    /** Policy limit used when the claim or its policy cannot be found on calculate (SETTLE-R49, OQ-03). */
    public static final double FALLBACK_POLICY_LIMIT = 10000;

    private LegacyInputs() {
    }

    /** {@code Integer.parseInt} with a silent fallback (SETTLE-R11). */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException failure) {
            return fallback;
        }
    }

    /** {@code Double.parseDouble} with a silent fallback (SETTLE-R12, SETTLE-R13). */
    public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException failure) {
            return fallback;
        }
    }

    /**
     * The action-level blank check applied before the calculator sees the deductible
     * (SETTLE-R16); a whitespace-only value passes this check and is trimmed to 0.00 by the
     * calculator instead (SETTLE-R17).
     */
    public static String deductibleOrZero(String value) {
        return value == null || value.isEmpty() ? "0" : value;
    }
}
