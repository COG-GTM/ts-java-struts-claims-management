package com.northstar.payment.web;

/**
 * Field value formatting, reproducing FieldTag.formatValue
 * (src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-76) and the
 * HTML escaping of {@code <bean:write>} so that the JSON values equal the text
 * the transcripts and the live proof record. The markup itself is presentation
 * and is out of parity scope (ADR-002, "How parity is judged").
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R05, PAY-R20 — {@code type="money"} is {@code String.format("%.2f", ...)},
 *       so 1.005 prints 1.01, -250 prints -250.00 and NaN prints NaN
 *       (FieldTag.java:48-58; issue.jsp; remittance.jsp).</li>
 *   <li>PAY-R16 — ids print as {@code type="integer"} and the issued date as
 *       {@code type="date"} yyyy-MM-dd (FieldTag.java:60-75; detail.jsp).</li>
 *   <li>PAY-R18 — payee and method print through {@code <bean:write>}, which
 *       escapes {@code & < > " '} and keeps case (detail.jsp:14,18).</li>
 * </ul>
 */
public final class FieldFormatter {
    private FieldFormatter() {
    }

    /** FieldTag.java:48-58: two decimals; an unparseable value prints unchanged. */
    public static String money(double value) {
        return String.format("%.2f", value);
    }

    /** FieldTag.java:60-66: an integer value prints as itself. */
    public static String integer(int value) {
        return Integer.toString(value);
    }

    /** FieldTag.java:67-75: a yyyy-MM-dd DATE column prints as yyyy-MM-dd. */
    public static String date(String value) {
        return value == null ? "" : value;
    }

    /** FieldTag.java:79-85: text prints as itself, null as the empty string. */
    public static String text(String value) {
        return value == null ? "" : value;
    }

    /** {@code <bean:write filter="true">} (the Struts default) escaping. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
