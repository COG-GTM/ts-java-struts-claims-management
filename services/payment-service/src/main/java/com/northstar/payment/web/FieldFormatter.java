package com.northstar.payment.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * The {@code <ns:field>} display rules of
 * src/main/java/com/northstar/claims/web/tag/FieldTag.java, applied to the
 * JSON values so the service answers what the JSP would have printed
 * (PAY-R27, PAY-R28).
 */
public final class FieldFormatter {

    private FieldFormatter() {
    }

    /** PAY-R27: two decimals (FieldTag.java:52-59). */
    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** PAY-R28: {@code type="integer"} truncates through a double (FieldTag.java:60-66). */
    public static String integer(int value) {
        return String.valueOf((long) value);
    }

    /** PAY-R28: null renders as the empty string (FieldTag.java:79-85). */
    public static String text(String value) {
        return value == null ? "" : value;
    }

    /** PAY-R28: {@code type="date"} reformats yyyy-MM-dd, else passes through (FieldTag.java:67-75). */
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
