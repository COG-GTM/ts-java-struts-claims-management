package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;
import com.northstar.claims.util.ClaimsValidator;

/** Verifies the shared field checks used by imports and form beans. */
public class ClaimsValidatorTest {

    private final ClaimsValidator validator = new ClaimsValidator();

    @Test
    public void requiredRejectsNullAndBlankValues() {
        assertFalse(validator.required("claimant", null));
        assertFalse(validator.required("description", "   "));
        assertTrue(validator.required("lossType", "WATER"));
        assertEquals(2, validator.messages().size());
        assertEquals("claimant is required", validator.firstMessage());
        assertEquals("description is required", validator.messages().get(1));
    }

    @Test
    public void numberAcceptsDecimalsAndRejectsText() {
        assertTrue(validator.number("reserve", "1250.75"));
        assertTrue(validator.valid());
        assertFalse(validator.number("reserve", "12x"));
        assertFalse(validator.number("deductible", null));
        assertEquals("reserve must be numeric", validator.firstMessage());
        assertEquals(2, validator.messages().size());
    }

    @Test
    public void integerRejectsDecimalValues() {
        assertTrue(validator.integer("claimId", "119"));
        assertFalse(validator.integer("claimId", "11.9"));
        assertFalse(validator.integer("policyId", ""));
        assertEquals("claimId must be an integer", validator.firstMessage());
        assertFalse(validator.valid());
    }

    @Test
    public void dateRequiresDisplayPattern() {
        assertTrue(validator.date("lossDate", "03/15/2019"));
        assertTrue(validator.valid());
        assertFalse(validator.date("lossDate", "2019-03-15"));
        assertFalse(validator.date("lossDate", "yesterday"));
        assertEquals("lossDate must be a date", validator.firstMessage());
        assertEquals(2, validator.messages().size());
    }

    @Test
    public void lengthAllowsNullAndExactMaximum() {
        assertTrue(validator.length("name", null, 3));
        assertTrue(validator.length("name", "abc", 3));
        assertFalse(validator.length("name", "abcd", 3));
        assertEquals("name is too long", validator.firstMessage());
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
        assertTrue(validator.messages().isEmpty());
    }

    @Test
    public void ageInDaysCountsWholeDays() {
        assertEquals(31, validator.ageInDays("2019-01-01", "2019-02-01"));
        assertEquals(0, validator.ageInDays("2019-02-01", "2019-02-01"));
        assertEquals(-10, validator.ageInDays("2019-02-11", "2019-02-01"));
    }

    @Test
    public void ageInDaysReturnsZeroForUnparseableDates() {
        assertEquals(0, validator.ageInDays("not-a-date", "2019-02-01"));
        assertEquals(0, validator.ageInDays(null, "2019-02-01"));
    }

    @Test
    public void summaryReportsValidityAndErrorCount() {
        assertEquals("valid=true, errors=0", validator.summary());
        validator.required("claimant", null);
        validator.number("reserve", "x");
        assertEquals("valid=false, errors=2", validator.summary());
    }
}
