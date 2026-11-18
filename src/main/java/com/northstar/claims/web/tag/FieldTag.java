package com.northstar.claims.web.tag;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * Writes a stable span around values consumed by operator tools.
 */
public class FieldTag extends TagSupport {

    private String name;
    private String value;
    private String type = "text";

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setType(String type) {
        if (type == null || type.length() == 0) {
            this.type = "text";
        } else {
            this.type = type;
        }
    }

    /** Formats only the value type requested by the page. */
    public int doStartTag() throws JspException {
        try {
            String text = formatValue(value);
            pageContext.getOut().print("<span id=\"f_");
            pageContext.getOut().print(escape(name));
            pageContext.getOut().print("\">");
            pageContext.getOut().print(escape(text));
            pageContext.getOut().print("</span>");
            return SKIP_BODY;
        } catch (Exception failure) {
            throw new JspException(failure);
        }
    }

    private String formatValue(String source) {
        if (source == null) {
            return "";
        }
        if ("money".equals(type)) {
            try {
                return String.format("%.2f", new Object[] {
                        Double.valueOf(Double.parseDouble(source)) });
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

    public void release() {
        name = null;
        value = null;
        type = "text";
        super.release();
    }
}
