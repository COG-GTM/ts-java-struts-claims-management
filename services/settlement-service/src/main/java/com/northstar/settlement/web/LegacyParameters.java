package com.northstar.settlement.web;

/**
 * The lenient request-parameter coercion of the Struts actions, which read raw
 * request parameters and ignore the form bean
 * (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-45;
 * src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23,32-37).
 *
 * <p>Implements SPEC-SETTLE-001:
 * <ul>
 *   <li>SETTLE-R02 — claimId parses as an integer, else 119.</li>
 *   <li>SETTLE-R03 — coveredAmount parses as a double, else 5000.</li>
 *   <li>SETTLE-R04 — depreciation parses as a double, else 0.</li>
 *   <li>SETTLE-R05 — a null or empty deductible is replaced by the string "0"
 *       by the action before the calculator sees it
 *       (SettlementCalculateAction.java:34-37).</li>
 *   <li>SETTLE-R06 (v2) — a non-blank deductible that does not parse is
 *       rejected up front rather than reaching
 *       {@link Double#parseDouble} inside the calculator
 *       (docs/changes/CHG-001-invalid-deductible.md).</li>
 * </ul>
 */
public final class LegacyParameters {

    public static final int DEFAULT_CLAIM_ID = 119;
    public static final double DEFAULT_COVERED_AMOUNT = 5000;
    public static final double DEFAULT_DEPRECIATION = 0;

    private LegacyParameters() {
    }

    /** SETTLE-R02: {@code integer(value, fallback)} of ClaimsActionSupport:31-37. */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** SETTLE-R03, SETTLE-R04: {@code decimal(value, fallback)} of ClaimsActionSupport:39-45. */
    public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** SETTLE-R05: null or empty becomes "0"; anything else is passed through. */
    public static String deductibleOrZero(String value) {
        return value == null || value.length() == 0 ? "0" : value;
    }

    /**
     * SETTLE-R06 (v2): true when the deductible is not blank and does not parse
     * as a number. Blank — null, empty or whitespace only — is not invalid; it
     * is the 0 of SETTLE-R05.
     */
    public static boolean isDeductibleInvalid(String value) {
        if (value == null || value.trim().length() == 0) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return false;
        } catch (NumberFormatException notANumber) {
            return true;
        }
    }
}
