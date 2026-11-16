package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.util.Utils;

/** Verifies the conversion helpers shared by the request handlers. */
public class UtilsTest {

    private static Locale savedLocale;

    @BeforeClass
    public static void pinLocale() {
        savedLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterClass
    public static void restoreLocale() {
        Locale.setDefault(savedLocale);
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    @Test
    public void isBlankTreatsWhitespaceAsBlank() {
        assertTrue(Utils.isBlank(null));
        assertTrue(Utils.isBlank(""));
        assertTrue(Utils.isBlank(" \t "));
        assertFalse(Utils.isBlank(" x "));
    }

    @Test
    public void toIntFallsBackToZero() {
        assertEquals(42, Utils.toInt("42"));
        assertEquals(-7, Utils.toInt("-7"));
        assertEquals(0, Utils.toInt(null));
        assertEquals(0, Utils.toInt("  "));
        assertEquals(0, Utils.toInt("4.2"));
        assertEquals(0, Utils.toInt("abc"));
    }

    @Test
    public void toDoubleFallsBackToZero() {
        assertEquals(1250.75, Utils.toDouble("1250.75"), 0.0);
        assertEquals(0.0, Utils.toDouble(null), 0.0);
        assertEquals(0.0, Utils.toDouble(""), 0.0);
        assertEquals(0.0, Utils.toDouble("$10"), 0.0);
    }

    @Test
    public void nullSafeStringHelpers() {
        assertEquals("", Utils.safe(null));
        assertEquals("ABC", Utils.upper("abc"));
        assertEquals("", Utils.upper(null));
        assertEquals("abc", Utils.lower("ABC"));
        assertEquals("x", Utils.trim("  x "));
        assertEquals("", Utils.trim(null));
        assertTrue(Utils.equalsText(null, ""));
        assertFalse(Utils.equalsText("a", "A"));
        assertEquals("fallback", Utils.defaultValue("  ", "fallback"));
        assertEquals("value", Utils.defaultValue("value", "fallback"));
    }

    @Test
    public void formatsMoneyAndRoundsToCents() {
        assertEquals("1234.50", Utils.formatMoney(1234.5));
        assertEquals("0.13", Utils.formatMoney(0.125));
        assertEquals(2.35, Utils.round(2.346), 0.0);
        assertEquals(2.34, Utils.round(2.344), 0.0);
        assertEquals(-1.5, Utils.round(-1.5), 0.0);
    }

    @Test
    public void formatsAndParsesDates() throws Exception {
        assertEquals("", Utils.formatDate(null));
        assertEquals("2019-03-15", Utils.formatDate(date(2019, 3, 15)));
        assertEquals(date(2019, 3, 15), Utils.parseDate("03/15/2019"));
    }

    @Test(expected = ParseException.class)
    public void parseDateRejectsUnexpectedFormat() throws Exception {
        Utils.parseDate("15.03.2019");
    }

    @Test
    public void dateArithmetic() throws Exception {
        assertEquals("2019-03-02", Utils.formatDate(
                Utils.addDays(date(2019, 2, 28), 2)));
        assertEquals("2019-02-27", Utils.formatDate(
                Utils.addDays(date(2019, 3, 1), -2)));
        assertEquals(32, Utils.dayOfYear(date(2019, 2, 1)));
        Calendar calendar = Utils.calendar("02/03/2019");
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH));
        assertEquals(3, calendar.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void joinUsesSeparatorBetweenValues() {
        List values = new ArrayList();
        assertEquals("", Utils.join(values, ","));
        values.add("OPEN");
        assertEquals("OPEN", Utils.join(values, ","));
        values.add(new Integer(3));
        assertEquals("OPEN, 3", Utils.join(values, ", "));
    }

    @Test
    public void padAndClaimNumber() {
        assertEquals("00007", Utils.pad(7, 5));
        assertEquals("123456", Utils.pad(123456, 5));
        assertEquals("CLM-00119", Utils.claimNumber(119));
    }

    @Test
    public void isPositiveExcludesZero() {
        assertTrue(Utils.isPositive(0.01));
        assertFalse(Utils.isPositive(0));
        assertFalse(Utils.isPositive(-1));
    }

    @Test
    public void factoriesReturnEmptyMutableCollections() {
        assertTrue(Utils.newList().isEmpty());
        assertTrue(Utils.newMap().isEmpty());
        assertTrue(Utils.newVector().isEmpty());
        assertEquals("reserve=10.0", Utils.logValue("reserve", new Double(10)));
    }
}
