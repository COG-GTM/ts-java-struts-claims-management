package com.northstar.settlement.domain;

/**
 * Request parameter coercions copied from ClaimsActionSupport and
 * SettlementCalculator (SETTLE-R02, R03, R04, R06). Numbers are parsed with
 * {@link Double#parseDouble} so that every lexical form the Struts screens
 * accept ({@code 1d}, {@code 0x1.0p0}, {@code Infinity}, {@code NaN}) is
 * accepted here too, and nothing else is (QUIRK-08).
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
     * SettlementCalculator: blank is zero, anything else must parse or the
     * request fails (SETTLE-R04, SETTLE-R06).
     */
    public static double deductible(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        return Double.parseDouble(value);
    }
}
