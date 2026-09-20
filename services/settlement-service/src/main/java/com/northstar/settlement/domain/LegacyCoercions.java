package com.northstar.settlement.domain;

/**
 * Request-parameter coercions the Struts actions performed through
 * {@code ClaimsActionSupport.integer}/{@code decimal} (SPEC-SETTLE-001 section 3.2).
 *
 * <p>These are legacy-faithful: a missing or unparseable value silently becomes the
 * fallback instead of a validation error (SETTLE-R11, SETTLE-R12, SETTLE-R13).
 */
public final class LegacyCoercions {

    /** SETTLE-R11: claimId falls back to 119 on all three settlement actions. */
    public static final int DEFAULT_CLAIM_ID = 119;

    /** SETTLE-R12: coveredAmount falls back to 5000. */
    public static final double DEFAULT_COVERED_AMOUNT = 5000;

    /** SETTLE-R13: depreciation falls back to 0. */
    public static final double DEFAULT_DEPRECIATION = 0;

    /** SETTLE-R14: policy limit used by calculate when the claim or policy is missing. */
    public static final double FALLBACK_POLICY_LIMIT = 10000;

    private LegacyCoercions() {
    }

    /**
     * Legacy-faithful {@code ClaimsActionSupport.integer}: {@code Integer.parseInt} with
     * any failure (null, blank, non-numeric, overflow) mapped to the fallback (SETTLE-R11).
     */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            return fallback;
        }
    }

    /**
     * Legacy-faithful {@code ClaimsActionSupport.decimal}: {@code Double.parseDouble} with
     * any failure mapped to the fallback (SETTLE-R12, SETTLE-R13, SETTLE-R19 - no range checks).
     */
    public static double decimal(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException failure) {
            return fallback;
        }
    }

    /**
     * Legacy-faithful deductible handling from {@code SettlementCalculateAction} lines 34-38:
     * a null or empty deductible becomes {@code "0"} (SETTLE-R16); anything else, including
     * whitespace (SETTLE-R17) or garbage (SETTLE-R18, save route), is passed through
     * untouched for the calculator to parse.
     */
    public static String deductible(String value) {
        if (value == null || value.length() == 0) {
            return "0";
        }
        return value;
    }

    /**
     * SETTLE-R18 v2 (CHG-001): a non-blank deductible that {@code Double.parseDouble}
     * rejects. Absent, empty and whitespace-only values are blank and stay 0.00
     * (SETTLE-R16, SETTLE-R17), so they are never invalid; "valid number" is exactly
     * what the calculator has always accepted.
     */
    public static boolean isInvalidDeductible(String value) {
        if (value == null || value.trim().length() == 0) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return false;
        } catch (NumberFormatException failure) {
            return true;
        }
    }
}
