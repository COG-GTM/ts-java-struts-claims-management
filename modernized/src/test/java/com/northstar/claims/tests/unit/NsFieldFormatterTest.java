package com.northstar.claims.tests.unit;

import com.northstar.claims.support.FieldFormatter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The `ns` fragment helper: emits the exact legacy FieldTag/marker strings the harness extracts. */
class NsFieldFormatterTest {
    private final FieldFormatter ns = new FieldFormatter();

    private static String span(String name, String value) {
        return "<span id=\"f_" + name + "\">" + value + "</span>";
    }

    @Test
    void moneyHasTwoDecimalsFromNumbersOrNumericStrings() {
        assertEquals(span("a", "4500.00"), ns.field("a", 4500, "money"));
        assertEquals(span("a", "4500.00"), ns.field("a", "4500", "money"));
        assertEquals(span("a", "0.10"), ns.field("a", 0.1, "money"));
        assertEquals(span("a", "1.01"), ns.field("a", "1.005", "money"));
    }

    @Test
    void integerTruncatesTowardZero() {
        assertEquals(span("n", "121"), ns.field("n", 121, "integer"));
        assertEquals(span("n", "3"), ns.field("n", 3.0, "integer"));
        assertEquals(span("n", "3"), ns.field("n", "3.99", "integer"));
        assertEquals(span("n", "-2"), ns.field("n", -2.9, "integer"));
    }

    @Test
    void dateIsLenientIsoRoundTrip() {
        assertEquals(span("d", "2019-03-02"), ns.field("d", "2019-03-02", "date"));
        assertEquals(span("d", "2019-03-02"), ns.field("d", "2019-02-30", "date"));
        // Trailing text after a parsable prefix is dropped by SimpleDateFormat.parse.
        assertEquals(span("d", "2019-01-15"), ns.field("d", "2019-01-15 00:00:00", "date"));
    }

    @Test
    void textAndUnknownTypesUseStringValueOf() {
        assertEquals(span("t", "Claimant 119"), ns.field("t", "Claimant 119", "text"));
        assertEquals(span("t", "1.0"), ns.field("t", 1.0, "text"));
        assertEquals(span("t", "true"), ns.field("t", true, null));
    }

    @Test
    void nonNumericValuesPassThroughUnchanged() {
        assertEquals(span("m", "N/A"), ns.field("m", "N/A", "money"));
        assertEquals(span("m", "1,500.00"), ns.field("m", "1,500.00", "money"));
        assertEquals(span("i", "abc"), ns.field("i", "abc", "integer"));
        assertEquals(span("d", "04/01/2019"), ns.field("d", "04/01/2019", "date"));
        assertEquals(span("m", ""), ns.field("m", null, "money"));
    }

    @Test
    void escapesOnlyAmpersandLessThanGreaterThanAndDoubleQuote() {
        assertEquals(span("x", "Smith &amp; Sons &lt;b&gt;&quot;Ltd&quot;"),
                ns.field("x", "Smith & Sons <b>\"Ltd\"", "text"));
        // Apostrophes are NOT escaped (Thymeleaf th:text would emit &#39;, which breaks parity).
        assertEquals(span("x", "O'Brien"), ns.field("x", "O'Brien", "text"));
        // Already-escaped input is escaped again, exactly like the legacy tag.
        assertEquals(span("x", "&amp;amp;"), ns.field("x", "&amp;", "text"));
    }

    @Test
    void viewAndErrorMarkersKeepLegacyJspPathsAndKeys() {
        assertEquals("<!-- ns:view /WEB-INF/jsp/settlement/calculate.jsp -->", ns.view("/WEB-INF/jsp/settlement/calculate.jsp"));
        assertEquals("<!-- ns:error errors.claimant.required -->", ns.error("errors.claimant.required"));
    }
}
