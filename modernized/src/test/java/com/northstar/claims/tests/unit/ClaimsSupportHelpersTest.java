package com.northstar.claims.tests.unit;

import com.northstar.claims.domain.Claim;
import com.northstar.claims.domain.Policy;
import com.northstar.claims.support.ClaimsSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure ports of ClaimsActionSupport helpers; no database is needed for these. */
class ClaimsSupportHelpersTest {
    private final ClaimsSupport support = new ClaimsSupport(null, null);

    @Test
    void lenientDateParsingRollsFebruary30IntoMarch2() {
        assertEquals("2019-03-02", support.normalizedDate("02/30/2019"));
        assertEquals("2019-02-28", support.normalizedDate("02/28/2019"));
        // Lenient month overflow too: month 13 of 2018 is January 2019.
        assertEquals("2019-01-15", support.normalizedDate("13/15/2018"));
    }

    @Test
    void blankNullAndUnparseableDatesDefaultToOperatorDate() {
        assertEquals("2019-04-01", support.normalizedDate(""));
        assertEquals("2019-04-01", support.normalizedDate(null));
        assertEquals("2019-04-01", support.normalizedDate("tomorrow"));
        // Whitespace is not blank for normalizedDate (length check only) and fails to parse.
        assertEquals("2019-04-01", support.normalizedDate(" "));
        assertEquals("2019-04-01", support.operatorDate());
        assertEquals("2019-04-01", support.reportDate());
    }

    @Test
    void validDateShapeChecksOnlyTheDigitPattern() {
        assertTrue(support.validDateShape("02/30/2019"));
        assertTrue(support.validDateShape("99/99/9999"));
        assertFalse(support.validDateShape("2019-02-01"));
        assertFalse(support.validDateShape("2/1/2019"));
        assertFalse(support.validDateShape(""));
        assertFalse(support.validDateShape(null));
    }

    @Test
    void integerFallsBackOnAnyParseFailure() {
        assertEquals(42, support.integer("42", 0));
        assertEquals(-1, support.integer("abc", -1));
        assertEquals(7, support.integer(null, 7));
        assertEquals(7, support.integer("", 7));
        assertEquals(7, support.integer("4.5", 7));
        assertEquals(7, support.integer(" 42", 7));
    }

    @Test
    void decimalFallsBackOnAnyParseFailure() {
        assertEquals(4500.0, support.decimal("4500.00", 0));
        assertEquals(1.5, support.decimal(" 1.5 ", 0));
        assertEquals(-1.0, support.decimal("1,500.00", -1));
        assertEquals(-1.0, support.decimal(null, -1));
        assertEquals(-1.0, support.decimal("", -1));
    }

    @Test
    void moneyAlwaysUsesUsLocaleAndTwoDecimals() {
        assertEquals("1000.00", support.money(1000));
        assertEquals("1.01", support.money(1.005));
        assertEquals("0.00", support.money(0));
        assertEquals("-2.50", support.money(-2.5));
    }

    @Test
    void textAndStatusDefaults() {
        assertTrue(support.hasText(" x "));
        assertFalse(support.hasText("  "));
        assertEquals("x", support.defaultText(" x ", "d"));
        assertEquals("d", support.defaultText(null, "d"));
        assertEquals("OPEN", support.normalizeStatus(" "));
        assertEquals("INVESTIGATING", support.normalizeStatus(" investigating "));
        assertEquals("CHECK", support.normalizeMethod(null));
        assertEquals("EFT", support.normalizeMethod("eft"));
        assertEquals("WATER", support.normalizeLossType(""));
        assertEquals("'O Brien'", support.quote("O'Brien"));
        assertEquals("null", support.quote(null));
        assertTrue(support.approvedStatus("closed"));
        assertFalse(support.approvedStatus("OPEN"));
        assertTrue(support.openStatus("Investigating"));
        assertTrue(support.financialAmount(0));
        assertFalse(support.financialAmount(100000000.0));
        assertFalse(support.financialAmount(-0.01));
        assertEquals("fallback", support.firstValue(List.of(), "fallback"));
        assertEquals("a", support.firstValue(List.of("a"), "fallback"));
        assertNotNull(support.safeList(null));
        assertEquals("Unknown claim", support.claimLabel(null));
        assertEquals("Unknown policy", support.policyLabel(null));
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-00121");
        claim.setClaimantName("Jane");
        assertEquals("CLM-00121 - Jane", support.claimLabel(claim));
        Policy policy = new Policy();
        policy.setPolicyNumber("NS-09001");
        policy.setLineOfBusiness("AUTO");
        assertEquals("NS-09001 - AUTO", support.policyLabel(policy));
    }
}
