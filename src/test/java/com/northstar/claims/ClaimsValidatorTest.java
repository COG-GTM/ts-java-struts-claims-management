package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;
import com.northstar.claims.util.ClaimsValidator;

/** Covers the field checks shared by import jobs and form beans. */
public class ClaimsValidatorTest {

    private final ClaimsValidator validator = new ClaimsValidator();

    @Test
    public void newValidatorIsValidWithNoMessages() {
        assertTrue(validator.valid());
        assertEquals("", validator.firstMessage());
        assertTrue(validator.messages().isEmpty());
    }

    @Test
    public void requiredRejectsNullAndBlankValues() {
        assertFalse(validator.required("claimant", null));
        assertFalse(validator.required("description", "   "));
        assertTrue(validator.required("lossType", "FIRE"));
        List messages = validator.messages();
        assertEquals(2, messages.size());
        assertEquals("claimant is required", messages.get(0));
        assertEquals("description is required", messages.get(1));
        assertFalse(validator.valid());
    }

    @Test
    public void numberAcceptsDecimalsAndRejectsTextAndNull() {
        assertTrue(validator.number("amount", "1250.75"));
        assertTrue(validator.number("amount", "-3"));
        assertFalse(validator.number("amount", "12a"));
        assertFalse(validator.number("reserve", null));
        assertEquals("amount must be numeric", validator.firstMessage());
        assertEquals(2, validator.messages().size());
    }

    @Test
    public void integerRejectsDecimalsAndText() {
        assertTrue(validator.integer("claimId", "42"));
        assertFalse(validator.integer("claimId", "4.2"));
        assertFalse(validator.integer("policyId", ""));
        assertEquals("claimId must be an integer", validator.firstMessage());
        assertEquals("policyId must be an integer", validator.messages().get(1));
    }

    @Test
    public void dateRequiresMonthDayYearPattern() {
        assertTrue(validator.date("lossDate", "03/15/2019"));
        assertFalse(validator.date("lossDate", "2019-03-15"));
        assertFalse(validator.date("lossDate", "not a date"));
        assertEquals(2, validator.messages().size());
        assertEquals("lossDate must be a date", validator.firstMessage());
    }

    @Test
    public void lengthAllowsNullAndExactMaximum() {
        assertTrue(validator.length("name", null, 3));
        assertTrue(validator.length("name", "abc", 3));
        assertFalse(validator.length("name", "abcd", 3));
        assertEquals("name is too long", validator.firstMessage());
        assertEquals(1, validator.messages().size());
    }

    @Test
    public void messagesReturnsDefensiveCopy() {
        validator.required("claimant", "");
        List copy = validator.messages();
        copy.clear();
        assertEquals(1, validator.messages().size());
        assertFalse(validator.valid());
    }

    @Test
    public void clearResetsValidationState() {
        validator.required("claimant", null);
        validator.clear();
        assertTrue(validator.valid());
        assertEquals("", validator.firstMessage());
    }

    @Test
    public void summaryReportsValidityAndErrorCount() {
        assertEquals("valid=true, errors=0", validator.summary());
        validator.required("claimant", null);
        validator.number("amount", "x");
        assertEquals("valid=false, errors=2", validator.summary());
    }

    @Test
    public void ageInDaysCountsWholeDaysBetweenDatabaseDates() {
        assertEquals(30, validator.ageInDays("2019-01-01", "2019-01-31"));
        assertEquals(0, validator.ageInDays("2019-02-15", "2019-02-15"));
        assertEquals(-14, validator.ageInDays("2019-01-15", "2019-01-01"));
    }

    @Test
    public void ageInDaysReturnsZeroForUnparseableDates() {
        assertEquals(0, validator.ageInDays("01/15/2019", "2019-04-01"));
        assertEquals(0, validator.ageInDays(null, "2019-04-01"));
        assertTrue(validator.valid());
    }
}
