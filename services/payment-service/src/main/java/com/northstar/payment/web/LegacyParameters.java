package com.northstar.payment.web;

/**
 * The parameter coercion of ClaimsActionSupport, copied as written
 * (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-45).
 */
public final class LegacyParameters {
    private LegacyParameters() {
    }

    /** ClaimsActionSupport.java:31-37: missing or not an integer gives the fallback. */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** ClaimsActionSupport.java:39-45: anything Double.parseDouble accepts, as parsed. */
    public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception failure) {
            return fallback;
        }
    }
}
