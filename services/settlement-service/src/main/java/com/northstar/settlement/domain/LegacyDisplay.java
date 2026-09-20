package com.northstar.settlement.domain;

import java.util.Locale;

/**
 * Port of the {@code ns:field} tag formatting ({@code FieldTag.formatValue}) for the
 * value types the settlement JSPs use (SPEC-SETTLE-001 section 3.4). These strings
 * are the module's outbound contract: the transcripts compare them verbatim.
 */
public final class LegacyDisplay {

    private LegacyDisplay() {
    }

    /**
     * SETTLE-R34: {@code type="money"} renders {@code String.format("%.2f", double)}.
     * Legacy-faithful: this rounds the decimal expansion of the double half-up, which is
     * why an input of 1.005 displays as 1.01 while the calculator pays 1.00 (SETTLE-R29).
     */
    public static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * SETTLE-R35: {@code cappedAtLimit} is declared {@code type="money"} on the calculate
     * screen, but the formatter cannot parse "true"/"false" and echoes the literal text.
     */
    public static String flag(boolean value) {
        return String.valueOf(value);
    }

    /** {@code type="integer"} renders {@code String.valueOf((long) Double.parseDouble(source))}. */
    public static String integer(int value) {
        return String.valueOf((long) value);
    }
}
