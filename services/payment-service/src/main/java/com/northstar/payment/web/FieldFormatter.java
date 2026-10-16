package com.northstar.payment.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * The {@code <ns:field>} display rules of
 * src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-77, applied to
 * the JSON values so the service answers what the JSP would have printed
 * (PAY-R28). The HTML escaping of FieldTag.java:79-85 is not applied: the
 * values are JSON, not markup (ADR-002, "How parity is judged").
 */
public final class FieldFormatter {

    private FieldFormatter() {
    }

    /** PAY-R28: {@code String.format("%.2f", Double.parseDouble(value))} (FieldTag.java:52-58); NaN prints as "NaN" (PAY-R08). */
    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** PAY-R28: {@code type="integer"} is the parsed double cast to long (FieldTag.java:60-66). */
    public static String integer(int value) {
        return String.valueOf((long) value);
    }

    /** PAY-R28: a null value renders as the empty string (FieldTag.java:49-51). */
    public static String text(String value) {
        return value == null ? "" : value;
    }

    /** PAY-R28: {@code type="date"} re-parses and re-formats yyyy-MM-dd, else passes the text through (FieldTag.java:67-74). */
    public static String date(String value) {
        if (value == null) {
            return "";
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException notDate) {
            return value;
        }
    }
}
