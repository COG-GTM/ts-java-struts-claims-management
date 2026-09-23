package com.northstar.settlement.domain;

import java.util.Locale;

/**
 * Two-decimal money display (SETTLE-R10 v2). FieldTag formats
 * {@code String.format("%.2f", Double.parseDouble(String.valueOf(value)))},
 * which rounds the decimal representation half up: 1.005 displays as 1.01
 * while the calculator rounds the same double to 1.00 (QUIRK-08).
 */
public final class LegacyDisplay {

    private LegacyDisplay() {
    }

    public static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
