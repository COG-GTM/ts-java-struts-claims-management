package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.text.ParseException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.util.LegacyFormatHelper;

/** Verifies the date and display conversions used by batch and screen code. */
public class LegacyFormatHelperTest {

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
    public void formatsDatabaseAndDisplayDates() {
        Date value = date(2019, 4, 1);
        assertEquals("2019-04-01", LegacyFormatHelper.databaseDate(value));
        assertEquals("04/01/2019", LegacyFormatHelper.displayDate(value));
    }

    @Test
    public void nullDatesFormatAsEmptyText() {
        assertEquals("", LegacyFormatHelper.databaseDate(null));
        assertEquals("", LegacyFormatHelper.displayDate(null));
    }

    @Test
    public void parsesBothDatePatternsToTheSameDay() throws Exception {
        Date fromDatabase = LegacyFormatHelper.parseDatabaseDate("2019-04-01");
        Date fromDisplay = LegacyFormatHelper.parseDisplayDate("04/01/2019");
        assertEquals(fromDatabase, fromDisplay);
        assertEquals(date(2019, 4, 1), fromDatabase);
    }

    @Test(expected = ParseException.class)
    public void parseDatabaseDateRejectsDisplayShape() throws Exception {
        LegacyFormatHelper.parseDatabaseDate("04/01/2019");
    }

    @Test(expected = ParseException.class)
    public void parseDisplayDateRejectsGarbage() throws Exception {
        LegacyFormatHelper.parseDisplayDate("not a date");
    }

    @Test
    public void monthNameAndYearMonth() {
        Date value = date(2019, 2, 14);
        assertEquals("February", LegacyFormatHelper.monthName(value));
        assertEquals("2019-02", LegacyFormatHelper.yearMonth(value));
    }

    @Test
    public void beginningAndEndOfMonthHandleLeapYears() {
        assertEquals("2020-02-01", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.beginningOfMonth(date(2020, 2, 17))));
        assertEquals("2020-02-29", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.endOfMonth(date(2020, 2, 17))));
        assertEquals("2019-02-28", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.endOfMonth(date(2019, 2, 1))));
        assertEquals("2019-12-31", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.endOfMonth(date(2019, 12, 31))));
    }

    @Test
    public void addBusinessDaysSkipsWeekends() {
        // 2019-04-05 is a Friday.
        Date friday = date(2019, 4, 5);
        assertEquals("2019-04-08", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.addBusinessDays(friday, 1)));
        assertEquals("2019-04-12", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.addBusinessDays(friday, 5)));
        // 2019-04-06 is a Saturday.
        assertEquals("2019-04-08", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.addBusinessDays(date(2019, 4, 6), 1)));
    }

    @Test
    public void addBusinessDaysWithZeroOrNegativeLeavesDateUnchanged() {
        Date saturday = date(2019, 4, 6);
        assertEquals(saturday, LegacyFormatHelper.addBusinessDays(saturday, 0));
        assertEquals(saturday, LegacyFormatHelper.addBusinessDays(saturday, -3));
    }

    @Test
    public void formatsMoneyAndPercentage() {
        assertEquals("1500.00", LegacyFormatHelper.money(1500));
        assertEquals("0.13", LegacyFormatHelper.money(0.125001));
        assertEquals("62.50%", LegacyFormatHelper.percentage(0.625));
        assertEquals("0.00%", LegacyFormatHelper.percentage(0));
    }

    @Test
    public void parseParametersKeepsOnlyKeyValuePairs() {
        Map values = LegacyFormatHelper.parseParameters("a=1&b=&c&d=x=y");
        assertEquals(3, values.size());
        assertEquals("1", values.get("a"));
        assertEquals("", values.get("b"));
        assertEquals("x=y", values.get("d"));
        assertTrue(!values.containsKey("c"));
    }

    @Test
    public void parseParametersWithNullReturnsEmptyMap() {
        assertTrue(LegacyFormatHelper.parseParameters(null).isEmpty());
        assertTrue(LegacyFormatHelper.parseParameters("").isEmpty());
    }

    @Test
    public void normalizeCodeTrimsAndUppercases() {
        assertEquals("FIRE", LegacyFormatHelper.normalizeCode("  fire "));
        assertEquals("", LegacyFormatHelper.normalizeCode(null));
    }

    @Test
    public void claimLabelJoinsIdAndNumber() {
        assertEquals("7 - CLM-00007", LegacyFormatHelper.claimLabel(7, "CLM-00007"));
        assertEquals("7 - ", LegacyFormatHelper.claimLabel(7, null));
    }
}
