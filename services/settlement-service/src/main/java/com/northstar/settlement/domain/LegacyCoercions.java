package com.northstar.settlement.domain;

/**
 * Request parameter coercions copied from ClaimsActionSupport and
 * SettlementCalculator (SETTLE-R02, SETTLE-R03 v2, SETTLE-R04, SETTLE-R06 v2).
 * Numbers are parsed with {@link Double#parseDouble}, exactly as the legacy
 * code does, so every spelling that method accepts ({@code 1d},
 * {@code 0x1.0p0}, {@code Infinity}, {@code NaN}, surrounding whitespace) is
 * accepted here too. QUIRK-08.
 */
public final class LegacyCoercions {

    private LegacyCoercions() {
    }

    /** ClaimsActionSupport.integer: anything unparsable becomes the fallback. */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException failure) {
            return fallback;
        }
    }

    /** ClaimsActionSupport.decimal: anything unparsable becomes the fallback. */
    public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException failure) {
            return fallback;
        }
    }

    /**
     * SettlementCalculateAction passes {@code "0"} for a null or empty
     * deductible; SettlementCalculator then treats a blank string as zero and
     * parses anything else, so a non-numeric value throws
     * {@link NumberFormatException} (SETTLE-R04, SETTLE-R06 v2).
     */
    public static double deductible(String value) {
        if (value == null || value.trim().length() == 0) {
            return 0;
        }
        return Double.parseDouble(value);
    }
}
