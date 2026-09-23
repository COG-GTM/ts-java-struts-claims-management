package com.northstar.settlement.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Field value formatting, reproducing FieldTag.formatValue
 * (src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-76) so that the
 * JSON field values equal the {@code <span id="f_NAME">} text the transcripts
 * record. The span markup itself is presentation and is out of parity scope
 * (ADR-001, "How parity is judged").
 *
 * <p>Implements SPEC-SETTLE-001:
 * <ul>
 *   <li>SETTLE-R19 — a null value renders as the empty string
 *       (FieldTag.java:79-85).</li>
 *   <li>SETTLE-R20 — {@code type="money"} formats with {@code String.format("%.2f", ...)}
 *       (FieldTag.java:52-59).</li>
 *   <li>SETTLE-R21 — display rounding is not the calculation rounding of
 *       SETTLE-R15: covered 1.005 displays as 1.01 while its settlement is 1.00
 *       (transcript settlement_half_cent).</li>
 *   <li>SETTLE-R22 — a value that cannot be parsed as a number is printed
 *       unchanged by {@code type="money"}, which is why {@code cappedAtLimit},
 *       declared money on calculate.jsp:26, renders as {@code true} or
 *       {@code false} (FieldTag.java:52-58).</li>
 *   <li>SETTLE-R31 — detail renders ids as {@code type="integer"} and the
 *       calculated date as {@code type="date"} reformatted yyyy-MM-dd
 *       (FieldTag.java:60-75; detail.jsp:10-26).</li>
 * </ul>
 */
public final class FieldFormatter {

    private FieldFormatter() {
    }

    /** SETTLE-R20: two decimals. */
    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** SETTLE-R22: a boolean is not numeric, so money printing leaves it alone. */
    public static String money(boolean value) {
        return String.valueOf(value);
    }

    /** SETTLE-R31: {@code type="integer"} truncates through a double. */
    public static String integer(int value) {
        return String.valueOf((long) value);
    }

    /** SETTLE-R19: null renders as the empty string. */
    public static String text(String value) {
        return value == null ? "" : value;
    }

    /** SETTLE-R31: {@code type="date"} reformats yyyy-MM-dd, else passes through. */
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
