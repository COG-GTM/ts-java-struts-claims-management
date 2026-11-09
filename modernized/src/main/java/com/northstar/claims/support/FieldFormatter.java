package com.northstar.claims.support;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.springframework.stereotype.Component;

/** Emits the legacy field tag without Thymeleaf's additional apostrophe escaping. */
@Component("ns")
public class FieldFormatter {
    public String field(String name, Object value, String type) {
        String source = value == null ? null : String.valueOf(value);
        return "<span id=\"f_" + escape(name) + "\">"
                + escape(formatValue(source, type)) + "</span>";
    }

    public String view(String path) {
        return "<!-- ns:view " + path + " -->";
    }

    public String error(String key) {
        return "<!-- ns:error " + key + " -->";
    }

    private String formatValue(String source, String type) {
        if (source == null) {
            return "";
        }
        if ("money".equals(type)) {
            try {
                return String.format("%.2f", new Object[] {
                        new Double(Double.parseDouble(source)) });
            } catch (NumberFormatException notMoney) {
                return source;
            }
        }
        if ("integer".equals(type)) {
            try {
                return String.valueOf((long) Double.parseDouble(source));
            } catch (NumberFormatException notInteger) {
                return source;
            }
        }
        if ("date".equals(type)) {
            try {
                SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd");
                Date date = input.parse(source);
                return new SimpleDateFormat("yyyy-MM-dd").format(date);
            } catch (Exception notDate) {
                return source;
            }
        }
        return source;
    }

    private String escape(String source) {
        if (source == null) {
            return "";
        }
        return source.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

}
