package com.northstar.settlement.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reproduces the observable surface of the legacy settlement screens: the {@code ns:view}
 * marker that names the forward and the {@code <span id="f_NAME">} field spans the transcript
 * harness reads (SETTLE-R07, SETTLE-R08, SETTLE-R34, SETTLE-R35).
 *
 * <p>The JSP markup itself is not part of the contract, only the marker and the spans
 * (ADR-001, Context).
 */
public final class ScreenRenderer {

    private final String viewPath;
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final StringBuilder errors = new StringBuilder();

    public ScreenRenderer(String viewPath) {
        this.viewPath = viewPath;
    }

    /** Two-decimal money, as {@code FieldTag type="money"} formats it (SETTLE-R34). */
    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /**
     * {@code FieldTag type="money"} falls back to the raw text when the value is not a number,
     * which is how the boolean cap flag reaches the screen as {@code true}/{@code false}
     * (SETTLE-R35).
     */
    public static String moneyOrText(String value) {
        try {
            return money(Double.parseDouble(value));
        } catch (NumberFormatException notMoney) {
            return value;
        }
    }

    /** {@code FieldTag type="integer"}. */
    public static String integer(double value) {
        return String.valueOf((long) value);
    }

    /** {@code FieldTag type="date"}: an unparseable value is echoed unchanged. */
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

    public ScreenRenderer field(String name, String value) {
        fields.put(name, value);
        return this;
    }

    /** Emits the {@code ns:error} marker the harness collects as a validation key. */
    public ScreenRenderer error(String key) {
        errors.append("<!-- ns:error ").append(key).append(" -->");
        return this;
    }

    public String render() {
        StringBuilder html = new StringBuilder();
        html.append("<!-- ns:view ").append(viewPath).append(" -->");
        html.append(errors);
        html.append("<html><head><title>NorthStar Claims</title></head><body>");
        html.append("<table class=\"details\" cellpadding=\"3\" cellspacing=\"0\" border=\"1\">");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            html.append("<tr><th>").append(escape(entry.getKey())).append("</th><td>")
                    .append("<span id=\"f_").append(escape(entry.getKey())).append("\">")
                    .append(escape(entry.getValue())).append("</span></td></tr>");
        }
        html.append("</table></body></html>");
        return html.toString();
    }

    private static String escape(String source) {
        if (source == null) {
            return "";
        }
        return source.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
