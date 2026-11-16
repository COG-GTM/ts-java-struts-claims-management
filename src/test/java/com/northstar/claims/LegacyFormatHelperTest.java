package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import org.junit.Test;
import com.northstar.claims.util.LegacyFormatHelper;

/** Verifies the date and display conversions shared by batch and screen code. */
public class LegacyFormatHelperTest {

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    @Test
    public void formatsDatabaseAndDisplayDates() {
        Date value = date(2019, 3, 5);
        assertEquals("2019-03-05", LegacyFormatHelper.databaseDate(value));
        assertEquals("03/05/2019", LegacyFormatHelper.displayDate(value));
        assertEquals("2019-03", LegacyFormatHelper.yearMonth(value));
    }

    @Test
    public void nullDatesFormatAsBlank() {
        assertEquals("", LegacyFormatHelper.databaseDate(null));
        assertEquals("", LegacyFormatHelper.displayDate(null));
    }

    @Test
    public void parsesBothPatterns() throws Exception {
        assertEquals(date(2019, 4, 1),
                LegacyFormatHelper.parseDatabaseDate("2019-04-01"));
        assertEquals(date(2019, 4, 1),
                LegacyFormatHelper.parseDisplayDate("04/01/2019"));
    }

    @Test(expected = ParseException.class)
    public void parseDisplayDateRejectsDatabaseFormat() throws Exception {
        LegacyFormatHelper.parseDisplayDate("2019-04-01");
    }

    @Test
    public void monthBoundariesHonorLeapYears() {
        assertEquals("2020-02-01", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.beginningOfMonth(date(2020, 2, 17))));
        assertEquals("2020-02-29", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.endOfMonth(date(2020, 2, 17))));
        assertEquals("2019-02-28", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.endOfMonth(date(2019, 2, 3))));
    }

    @Test
    public void addBusinessDaysSkipsWeekends() {
        // 2019-03-01 is a Friday.
        assertEquals("2019-03-04", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.addBusinessDays(date(2019, 3, 1), 1)));
        assertEquals("2019-03-08", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.addBusinessDays(date(2019, 3, 1), 5)));
        // 2019-03-02 is a Saturday.
        assertEquals("2019-03-04", LegacyFormatHelper.databaseDate(
                LegacyFormatHelper.addBusinessDays(date(2019, 3, 2), 1)));
    }

    @Test
    public void addBusinessDaysWithZeroOrNegativeKeepsDate() {
        Date friday = date(2019, 3, 1);
        assertEquals(friday, LegacyFormatHelper.addBusinessDays(friday, 0));
        assertEquals(friday, LegacyFormatHelper.addBusinessDays(friday, -3));
    }

    @Test
    public void formatsMoneyAndPercentages() {
        assertEquals("1234.50", LegacyFormatHelper.money(1234.5));
        assertEquals("0.00", LegacyFormatHelper.money(0));
        assertEquals("12.50%", LegacyFormatHelper.percentage(0.125));
        assertEquals("-5.00%", LegacyFormatHelper.percentage(-0.05));
    }

    @Test
    public void parseParametersSplitsPairsOnFirstEquals() {
        Map values = LegacyFormatHelper.parseParameters(
                "claimId=119&filter=status=OPEN&flag");
        assertEquals("119", values.get("claimId"));
        assertEquals("status=OPEN", values.get("filter"));
        assertFalse(values.containsKey("flag"));
        assertEquals(2, values.size());
    }

    @Test
    public void parseParametersOfNullIsEmpty() {
        assertTrue(LegacyFormatHelper.parseParameters(null).isEmpty());
    }

    @Test
    public void normalizeCodeTrimsAndUppercases() {
        assertEquals("WATER", LegacyFormatHelper.normalizeCode("  water "));
        assertEquals("", LegacyFormatHelper.normalizeCode(null));
    }

    @Test
    public void claimLabelHandlesMissingNumber() {
        assertEquals("119 - CLM-00119",
                LegacyFormatHelper.claimLabel(119, "CLM-00119"));
        assertEquals("7 - ", LegacyFormatHelper.claimLabel(7, null));
    }
}
