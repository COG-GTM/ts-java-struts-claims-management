package com.northstar.payment.web;

import java.util.Locale;

/**
 * The lenient parameter coercion and field printing the payment actions and
 * JSPs use (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-45;
 * src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-74).
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R02, PAY-R14, PAY-R16, PAY-R19 — an id parses as an integer,
 *       else the action's default (119 for a claim, 61 for a payment).</li>
 *   <li>PAY-R04, PAY-R05 — an amount parses as a double, else the fallback;
 *       anything {@link Double#parseDouble} accepts is kept as is, including
 *       negatives, exponents and NaN.</li>
 *   <li>PAY-R20 — money prints with {@code %.2f}, so NaN prints as "NaN".</li>
 * </ul>
 */
public final class LegacyParameters {

    public static final int DEFAULT_CLAIM_ID = 119;
    public static final int DEFAULT_PAYMENT_ID = 61;

    private LegacyParameters() {
    }

    /** {@code integer(value, fallback)} of ClaimsActionSupport:31-37. */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** {@code decimal(value, fallback)} of ClaimsActionSupport:39-45. */
    public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** {@code type="money"}: two decimals. */
    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** {@code type="integer"}: truncated through a double. */
    public static String integer(int value) {
        return String.valueOf((long) value);
    }

    /** {@code type="text"}: null renders as the empty string. */
    public static String text(String value) {
        return value == null ? "" : value;
    }
}
