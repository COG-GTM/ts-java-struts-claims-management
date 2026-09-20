package com.northstar.settlement.domain;

import java.math.BigDecimal;

/**
 * Request parameter coercions copied from ClaimsActionSupport and
 * SettlementCalculateAction (SETTLE-R02, R03, R04, R06).
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
    public static BigDecimal decimal(String value, String fallback) {
        try {
            return new BigDecimal(value.trim());
        } catch (RuntimeException failure) {
            return new BigDecimal(fallback);
        }
    }

    /**
     * SettlementCalculator: blank is zero, anything else must parse or the
     * request fails (SETTLE-R04, SETTLE-R06).
     */
    public static BigDecimal deductible(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }
}
