package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.util.Utils;

/** Verifies the conversion helpers shared by the old request handlers. */
public class UtilsTest {

    private static Locale previousLocale;

    @BeforeClass
    public static void useUsLocale() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterClass
    public static void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    private static Date date(int year, int month, int day) {
        return new GregorianCalendar(year, month - 1, day).getTime();
    }

    @Test
    public void blankAndSafeTreatNullAndWhitespaceAsEmpty() {
        assertTrue(Utils.isBlank(null));
        assertTrue(Utils.isBlank(""));
        assertTrue(Utils.isBlank("   "));
        assertFalse(Utils.isBlank(" x "));
        assertEquals("", Utils.safe(null));
        assertEquals("abc", Utils.safe("abc"));
    }

    @Test
    public void toIntFallsBackToZeroForBlankOrNonNumeric() {
        assertEquals(42, Utils.toInt("42"));
        assertEquals(-7, Utils.toInt("-7"));
        assertEquals(0, Utils.toInt(null));
        assertEquals(0, Utils.toInt("  "));
        assertEquals(0, Utils.toInt("12.5"));
        assertEquals(0, Utils.toInt("abc"));
    }

    @Test
    public void toDoubleFallsBackToZeroForBlankOrNonNumeric() {
        assertEquals(12.5, Utils.toDouble("12.5"), 0.0);
        assertEquals(0.0, Utils.toDouble(null), 0.0);
        assertEquals(0.0, Utils.toDouble(""), 0.0);
        assertEquals(0.0, Utils.toDouble("1,000"), 0.0);
    }

    @Test
    public void formatsMoneyWithTwoDecimals() {
        assertEquals("1234.50", Utils.formatMoney(1234.5));
        assertEquals("0.00", Utils.formatMoney(0));
        assertEquals("-3.13", Utils.formatMoney(-3.125001));
    }

    @Test
    public void formatsAndParsesDates() throws Exception {
        assertEquals("", Utils.formatDate(null));
        assertEquals("2019-04-01", Utils.formatDate(date(2019, 4, 1)));
        assertEquals("2019-04-01", Utils.formatDate(Utils.parseDate("04/01/2019")));
    }

    @Test(expected = ParseException.class)
    public void parseDateRejectsDatabaseShapedText() throws Exception {
        Utils.parseDate("2019-04-01x");
    }

    @Test
    public void addDaysCrossesMonthAndYearBoundaries() {
        assertEquals("2019-03-01", Utils.formatDate(Utils.addDays(date(2019, 2, 28), 1)));
        assertEquals("2020-01-02", Utils.formatDate(Utils.addDays(date(2019, 12, 31), 2)));
        assertEquals("2019-03-31", Utils.formatDate(Utils.addDays(date(2019, 4, 1), -1)));
    }

    @Test
    public void caseAndTrimHelpersAreNullSafe() {
        assertEquals("ABC", Utils.upper("aBc"));
        assertEquals("", Utils.upper(null));
        assertEquals("abc", Utils.lower("ABC"));
        assertEquals("", Utils.lower(null));
        assertEquals("x y", Utils.trim("  x y  "));
        assertEquals("", Utils.trim(null));
    }

    @Test
    public void equalsTextTreatsNullAsEmpty() {
        assertTrue(Utils.equalsText(null, ""));
        assertTrue(Utils.equalsText("a", "a"));
        assertFalse(Utils.equalsText("a", "A"));
        assertFalse(Utils.equalsText(null, "a"));
    }

    @Test
    public void defaultValueReplacesBlankOnly() {
        assertEquals("fallback", Utils.defaultValue(null, "fallback"));
        assertEquals("fallback", Utils.defaultValue("  ", "fallback"));
        assertEquals(" kept ", Utils.defaultValue(" kept ", "fallback"));
    }

    @Test
    public void roundsToCents() {
        assertEquals(10.13, Utils.round(10.125), 0.0);
        assertEquals(10.12, Utils.round(10.124), 0.0);
        assertEquals(-2.5, Utils.round(-2.5), 0.0);
    }

    @Test
    public void collectionFactoriesReturnFreshEmptyInstances() {
        assertTrue(Utils.newList() instanceof ArrayList);
        assertTrue(Utils.newList().isEmpty());
        assertTrue(Utils.newMap() instanceof Hashtable);
        assertTrue(Utils.newVector() instanceof Vector);
        assertFalse(Utils.newList() == Utils.newList());
    }

    @Test
    public void joinUsesSeparatorBetweenValuesOnly() {
        assertEquals("", Utils.join(new ArrayList(), ","));
        assertEquals("a", Utils.join(Arrays.asList(new Object[] { "a" }), ","));
        List values = Arrays.asList(new Object[] { "a", new Integer(2), "c" });
        assertEquals("a, 2, c", Utils.join(values, ", "));
    }

    @Test
    public void padAndClaimNumberUseLeadingZeros() {
        assertEquals("00042", Utils.pad(42, 5));
        assertEquals("123456", Utils.pad(123456, 5));
        assertEquals("7", Utils.pad(7, 0));
        assertEquals("CLM-00007", Utils.claimNumber(7));
        assertEquals("CLM-12345", Utils.claimNumber(12345));
    }

    @Test
    public void isPositiveExcludesZero() {
        assertTrue(Utils.isPositive(0.01));
        assertFalse(Utils.isPositive(0));
        assertFalse(Utils.isPositive(-1));
    }

    @Test
    public void logValueReturnsNameValuePair() {
        assertEquals("claim=42", Utils.logValue("claim", new Integer(42)));
        assertEquals("claim=null", Utils.logValue("claim", null));
    }

    @Test
    public void calendarAndDayOfYearReadDisplayDates() throws Exception {
        Calendar calendar = Utils.calendar("04/01/2019");
        assertEquals(2019, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.APRIL, calendar.get(Calendar.MONTH));
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(91, Utils.dayOfYear(calendar.getTime()));
        assertEquals(1, Utils.dayOfYear(date(2020, 1, 1)));
        assertEquals(366, Utils.dayOfYear(date(2020, 12, 31)));
    }
}
