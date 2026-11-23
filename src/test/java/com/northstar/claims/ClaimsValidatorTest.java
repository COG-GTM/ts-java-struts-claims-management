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
        assertTrue(validator.messages().isEmpty());
        assertEquals("", validator.firstMessage());
    }

    @Test
    public void requiredRejectsNullAndBlank() {
        assertFalse(validator.required("claimant", null));
        assertFalse(validator.required("lossDate", "   "));
        assertTrue(validator.required("description", "Water damage"));
        assertFalse(validator.valid());
        List messages = validator.messages();
        assertEquals(2, messages.size());
        assertEquals("claimant is required", messages.get(0));
        assertEquals("lossDate is required", messages.get(1));
        assertEquals("claimant is required", validator.firstMessage());
    }

    @Test
    public void numberAcceptsDecimalsAndRejectsText() {
        assertTrue(validator.number("reserve", "1250.75"));
        assertTrue(validator.number("reserve", "-3"));
        assertFalse(validator.number("reserve", "12a"));
        assertFalse(validator.number("deductible", null));
        assertEquals(2, validator.messages().size());
        assertEquals("reserve must be numeric", validator.firstMessage());
    }

    @Test
    public void integerRejectsDecimalsAndNull() {
        assertTrue(validator.integer("claimId", "42"));
        assertFalse(validator.integer("claimId", "42.5"));
        assertFalse(validator.integer("policyId", null));
        assertEquals("claimId must be an integer", validator.firstMessage());
        assertEquals(2, validator.messages().size());
    }

    @Test
    public void dateAcceptsDisplayFormatAndRejectsGarbage() {
        assertTrue(validator.date("lossDate", "03/15/2019"));
        assertFalse(validator.date("lossDate", "not a date"));
        assertEquals("lossDate must be a date", validator.firstMessage());
    }

    @Test
    public void lengthRejectsOnlyValuesOverMaximum() {
        assertTrue(validator.length("code", null, 3));
        assertTrue(validator.length("code", "ABC", 3));
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
        validator.required("claimant", "");
        validator.clear();
        assertTrue(validator.valid());
        assertEquals("", validator.firstMessage());
    }

    @Test
    public void ageInDaysCountsCalendarDays() {
        assertEquals(17, validator.ageInDays("2019-03-15", "2019-04-01"));
        assertEquals(0, validator.ageInDays("2019-04-01", "2019-04-01"));
        assertEquals(-17, validator.ageInDays("2019-04-01", "2019-03-15"));
    }

    @Test
    public void ageInDaysReturnsZeroForUnparseableDates() {
        assertEquals(0, validator.ageInDays("garbage", "2019-04-01"));
        assertEquals(0, validator.ageInDays(null, "2019-04-01"));
    }

    @Test
    public void summaryReportsValidityAndErrorCount() {
        assertEquals("valid=true, errors=0", validator.summary());
        validator.required("claimant", null);
        validator.number("reserve", "x");
        assertEquals("valid=false, errors=2", validator.summary());
    }
}
