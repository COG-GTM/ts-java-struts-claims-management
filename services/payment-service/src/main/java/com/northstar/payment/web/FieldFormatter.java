package com.northstar.payment.web;

/**
 * Field values as FieldTag.formatValue prints them
 * (src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-75), so the JSON
 * values equal the {@code <span id="f_...">} texts of the legacy screens.
 */
public final class FieldFormatter {
    private FieldFormatter() {
    }

    /** {@code type="money"}: {@code String.format("%.2f")}; NaN prints NaN. */
    public static String money(double value) {
        return String.format("%.2f", value);
    }

    /** {@code type="integer"}. */
    public static String integer(int value) {
        return Integer.toString(value);
    }

    /** {@code type="date"}: the DATE column read as text is already yyyy-MM-dd. */
    public static String date(String value) {
        return value == null ? "" : value;
    }

    /** {@code type="text"}: null prints as the empty string. */
    public static String text(String value) {
        return value == null ? "" : value;
    }
}
