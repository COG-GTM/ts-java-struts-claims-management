package com.northstar.settlement.domain;

import java.util.Locale;

/**
 * Two-decimal money display (SETTLE-R10), formatted the way the legacy
 * {@code FieldTag} does: {@code String.format("%.2f", double)}. That formatter
 * rounds the shortest decimal form of the double half up, which is not the
 * same as the calculator's {@code Math.round} on the binary value
 * (docs/KNOWN_LEGACY_QUIRKS.md, QUIRK-02).
 */
public final class LegacyDisplay {

    private LegacyDisplay() {
    }

    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
