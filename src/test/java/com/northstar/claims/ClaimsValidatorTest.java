package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;
import com.northstar.claims.util.ClaimsValidator;

/** Verifies the field checks shared by import jobs and Struts form beans. */
public class ClaimsValidatorTest {

    private final ClaimsValidator validator = new ClaimsValidator();

    @Test
    public void newValidatorIsValidWithNoMessages() {
        assertTrue(validator.valid());
        assertTrue(validator.messages().isEmpty());
        assertEquals("", validator.firstMessage());
        assertEquals("valid=true, errors=0", validator.summary());
    }

    @Test
    public void requiredRejectsNullAndBlank() {
        assertTrue(validator.required("claimant", "Pat"));
        assertFalse(validator.required("claimant", null));
        assertFalse(validator.required("description", "   "));
        List messages = validator.messages();
        assertEquals(2, messages.size());
        assertEquals("claimant is required", messages.get(0));
        assertEquals("description is required", messages.get(1));
        assertFalse(validator.valid());
    }

    @Test
    public void numberAcceptsDecimalsAndRejectsText() {
        assertTrue(validator.number("reserve", "1500.25"));
        assertTrue(validator.number("reserve", "-3"));
        assertTrue(validator.valid());
        assertFalse(validator.number("reserve", "abc"));
        assertFalse(validator.number("reserve", null));
        assertEquals("reserve must be numeric", validator.firstMessage());
        assertEquals(2, validator.messages().size());
    }

    @Test
    public void integerRejectsDecimalsBlankAndNull() {
        assertTrue(validator.integer("claimId", "42"));
        assertFalse(validator.integer("claimId", "4.2"));
        assertFalse(validator.integer("claimId", ""));
        assertFalse(validator.integer("claimId", null));
        assertEquals(3, validator.messages().size());
        assertEquals("claimId must be an integer", validator.firstMessage());
    }

    @Test
    public void dateAcceptsDisplayPatternAndRejectsGarbage() {
        assertTrue(validator.date("lossDate", "04/01/2019"));
        assertTrue(validator.valid());
        assertFalse(validator.date("lossDate", "yesterday"));
        assertEquals("lossDate must be a date", validator.firstMessage());
    }

    @Test
    public void lengthEnforcesMaximumAndIgnoresNull() {
        assertTrue(validator.length("code", "ABC", 3));
        assertTrue(validator.length("code", null, 3));
        assertTrue(validator.length("code", "", 0));
        assertFalse(validator.length("code", "ABCD", 3));
        assertEquals("code is too long", validator.firstMessage());
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
    public void clearResetsState() {
        validator.required("claimant", null);
        validator.number("reserve", "x");
        assertEquals("valid=false, errors=2", validator.summary());
        validator.clear();
        assertTrue(validator.valid());
        assertEquals("", validator.firstMessage());
        assertEquals("valid=true, errors=0", validator.summary());
    }

    @Test
    public void ageInDaysCountsWholeDaysBetweenDatabaseDates() {
        assertEquals(0, validator.ageInDays("2019-04-01", "2019-04-01"));
        assertEquals(17, validator.ageInDays("2019-01-15", "2019-02-01"));
        assertEquals(365, validator.ageInDays("2018-04-01", "2019-04-01"));
        assertEquals(-17, validator.ageInDays("2019-02-01", "2019-01-15"));
    }

    @Test
    public void ageInDaysReturnsZeroWhenDatesCannotBeParsed() {
        assertEquals(0, validator.ageInDays("04/01/2019", "2019-04-01"));
        assertEquals(0, validator.ageInDays(null, "2019-04-01"));
        assertEquals(0, validator.ageInDays("2019-04-01", ""));
        assertTrue(validator.valid());
    }
}
