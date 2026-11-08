package com.northstar.claims;

import com.northstar.claims.support.FieldFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Golden cases follow FieldTag's String setter, formatting, and four-character escape contract. */
class FieldFormatterTest {
    private final FieldFormatter formatter = new FieldFormatter();

    @Test
    void matchesLegacyNumericAndDateFormatting() {
        assertEquals("<span id=\"f_amount\">1.01</span>", formatter.field("amount", 1.005, "money"));
        assertEquals("<span id=\"f_amount\">false</span>", formatter.field("amount", false, "money"));
        assertEquals("<span id=\"f_amount\">false</span>", formatter.field("amount", false, "integer"));
        assertEquals("<span id=\"f_count\">-2</span>", formatter.field("count", -2.9, "integer"));
        assertEquals("<span id=\"f_date\">2019-03-02</span>", formatter.field("date", "2019-02-30", "date"));
        assertEquals("<span id=\"f_date\">not a date</span>", formatter.field("date", "not a date", "date"));
        assertEquals("<span id=\"f_number\">1.0</span>", formatter.field("number", 1.0, "text"));
    }

    @Test
    void preservesEmptyNullAndOnlyLegacyEscapes() {
        assertEquals("<span id=\"f_\"></span>", formatter.field(null, null, "money"));
        assertEquals("<span id=\"f_x\"></span>", formatter.field("x", "", "integer"));
        assertEquals("<span id=\"f_&amp;&lt;&gt;&quot;'\">&amp;&lt;&gt;&quot;'</span>",
                formatter.field("&<>\"'", "&<>\"'", "text"));
        assertEquals("<!-- ns:view /WEB-INF/jsp/home.jsp -->", formatter.view("/WEB-INF/jsp/home.jsp"));
        assertEquals("<!-- ns:error errors.required -->", formatter.error("errors.required"));
    }

    @Test
    void moneyUsesDefaultLocaleNotForcedUs() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("<span id=\"f_x\">1,50</span>", formatter.field("x", 1.5, "money"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
