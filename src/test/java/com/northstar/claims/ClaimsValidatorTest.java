package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;
import com.northstar.claims.util.ClaimsValidator;

/** Verifies the shared field checks used by import jobs and form beans. */
public class ClaimsValidatorTest {

    private final ClaimsValidator validator = new ClaimsValidator();

    @Test
    public void newValidatorIsValidWithNoMessages() {
        assertTrue(validator.valid());
        assertEquals("", validator.firstMessage());
        assertTrue(validator.messages().isEmpty());
    }

    @Test
    public void requiredRejectsNullBlankAndWhitespace() {
        assertFalse(validator.required("claimant", null));
        assertFalse(validator.required("lossDate", ""));
        assertFalse(validator.required("description", "   "));
        assertTrue(validator.required("policy", "NS-00001"));
        List messages = validator.messages();
        assertEquals(3, messages.size());
        assertEquals("claimant is required", messages.get(0));
        assertEquals("description is required", messages.get(2));
        assertFalse(validator.valid());
    }

    @Test
    public void numberAcceptsDecimalsAndRejectsTextAndNull() {
        assertTrue(validator.number("amount", "1250.75"));
        assertTrue(validator.number("amount", "-3"));
        assertFalse(validator.number("amount", "12abc"));
        assertFalse(validator.number("reserve", null));
        assertEquals("amount must be numeric", validator.firstMessage());
        assertEquals(2, validator.messages().size());
    }

    @Test
    public void integerRejectsDecimalsAndBlank() {
        assertTrue(validator.integer("claimId", "119"));
        assertFalse(validator.integer("claimId", "11.9"));
        assertFalse(validator.integer("policyId", ""));
        assertEquals("claimId must be an integer", validator.firstMessage());
        assertEquals(2, validator.messages().size());
    }

    @Test
    public void dateUsesDisplayPattern() {
        assertTrue(validator.date("lossDate", "03/15/2019"));
        assertFalse(validator.date("lossDate", "2019-03-15x"));
        assertEquals("lossDate must be a date", validator.firstMessage());
    }

    @Test
    public void lengthAllowsNullAndBoundaryButRejectsLonger() {
        assertTrue(validator.length("note", null, 5));
        assertTrue(validator.length("note", "12345", 5));
        assertFalse(validator.length("note", "123456", 5));
        assertEquals("note is too long", validator.firstMessage());
        assertEquals(1, validator.messages().size());
    }

    @Test
    public void messagesReturnsDefensiveCopyAndClearResets() {
        validator.required("claimant", null);
        List copy = validator.messages();
        copy.clear();
        assertEquals(1, validator.messages().size());
        validator.clear();
        assertTrue(validator.valid());
        assertEquals("", validator.firstMessage());
    }

    @Test
    public void ageInDaysCountsCalendarDays() {
        assertEquals(17, validator.ageInDays("2019-03-15", "2019-04-01"));
        assertEquals(0, validator.ageInDays("2019-04-01", "2019-04-01"));
        assertEquals(-31, validator.ageInDays("2019-04-01", "2019-03-01"));
    }

    @Test
    public void ageInDaysReturnsZeroForUnparseableDates() {
        assertEquals(0, validator.ageInDays("not-a-date", "2019-04-01"));
        assertEquals(0, validator.ageInDays("2019-04-01", null));
    }

    @Test
    public void summaryReportsValidityAndErrorCount() {
        assertEquals("valid=true, errors=0", validator.summary());
        validator.required("claimant", "");
        validator.number("amount", "x");
        assertEquals("valid=false, errors=2", validator.summary());
    }
}
