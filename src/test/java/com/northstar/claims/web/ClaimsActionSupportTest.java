package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.northstar.claims.TestDatabase;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Policy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Covers the shared parsing, normalization and JDBC helpers used by every action. */
public class ClaimsActionSupportTest {

    private final ClaimsActionSupport support = new ClaimsActionSupport() { };

    @BeforeClass
    public static void seedDatabase() throws Exception {
        TestDatabase.reset();
    }

    @AfterClass
    public static void releaseDatabase() throws Exception {
        TestDatabase.release();
    }

    @Test
    public void integerAndDecimalFallBackOnBadInput() {
        assertEquals(42, support.integer("42", -1));
        assertEquals(-1, support.integer("4.2", -1));
        assertEquals(-1, support.integer(null, -1));
        assertEquals(12.5, support.decimal("12.5", 0), 0.0001);
        assertEquals(7.0, support.decimal("abc", 7), 0.0001);
        assertEquals(7.0, support.decimal(null, 7), 0.0001);
    }

    @Test
    public void normalizedDateConvertsDisplayDates() {
        assertEquals("2019-03-10", support.normalizedDate("03/10/2019"));
        assertEquals("2019-04-01", support.normalizedDate(null));
        assertEquals("2019-04-01", support.normalizedDate(""));
        assertEquals("2019-04-01", support.normalizedDate("not a date"));
    }

    @Test
    public void validDateShapeRequiresTwoDigitMonthAndDay() {
        assertTrue(support.validDateShape("03/10/2019"));
        assertFalse(support.validDateShape("3/10/2019"));
        assertFalse(support.validDateShape("2019-03-10"));
        assertFalse(support.validDateShape(null));
    }

    @Test
    public void normalizersTrimUppercaseAndDefault() {
        assertEquals("CLOSED", support.normalizeStatus(" closed "));
        assertEquals("OPEN", support.normalizeStatus(null));
        assertEquals("EFT", support.normalizeMethod("eft"));
        assertEquals("CHECK", support.normalizeMethod(null));
        assertEquals("FIRE", support.normalizeLossType(" fire"));
        assertEquals("WATER", support.normalizeLossType(null));
    }

    @Test
    public void statusPredicatesIgnoreCase() {
        assertTrue(support.approvedStatus("approved"));
        assertTrue(support.approvedStatus("CLOSED"));
        assertFalse(support.approvedStatus("OPEN"));
        assertFalse(support.approvedStatus(null));
        assertTrue(support.openStatus("open"));
        assertTrue(support.openStatus("Investigating"));
        assertFalse(support.openStatus("DENIED"));
        assertFalse(support.openStatus(null));
    }

    @Test
    public void financialAmountBounds() {
        assertTrue(support.financialAmount(0.0));
        assertTrue(support.financialAmount(99999999.99));
        assertFalse(support.financialAmount(-0.01));
        assertFalse(support.financialAmount(100000000.0));
    }

    @Test
    public void textHelpers() {
        assertTrue(support.hasText(" a "));
        assertFalse(support.hasText("   "));
        assertFalse(support.hasText(null));
        assertEquals("value", support.defaultText("  value ", "fallback"));
        assertEquals("fallback", support.defaultText(" ", "fallback"));
        assertEquals("'O Brien'", support.quote("O'Brien"));
        assertEquals("null", support.quote(null));
        assertEquals("1234.50", support.money(1234.5));
        assertEquals("2019-04-01", support.operatorDate());
        assertEquals("2019-04-01", support.reportDate());
    }

    @Test
    public void listHelpers() {
        assertTrue(support.emptyList().isEmpty());
        List values = new ArrayList(Arrays.asList(new Object[] { Integer.valueOf(9), "x" }));
        assertEquals(values, support.safeList(values));
        assertTrue(support.safeList(null).isEmpty());
        assertEquals("9", support.firstValue(values, "none"));
        assertEquals("none", support.firstValue(new ArrayList(), "none"));
        assertEquals("none", support.firstValue(null, "none"));
    }

    @Test
    public void labelsDescribeClaimsAndPolicies() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-00001");
        claim.setClaimantName("Claimant 1");
        assertEquals("CLM-00001 - Claimant 1", support.claimLabel(claim));
        assertEquals("Unknown claim", support.claimLabel(null));
        Policy policy = new Policy();
        policy.setPolicyNumber("NS-09001");
        policy.setLineOfBusiness("AUTO");
        assertEquals("NS-09001 - AUTO", support.policyLabel(policy));
        assertEquals("Unknown policy", support.policyLabel(null));
    }

    @Test
    public void requestAndSessionHelpers() {
        ServletStubs.Request stub = new ServletStubs.Request().param("claimId", "  12 ");
        assertEquals("12", support.safeParameter(stub.asRequest(), "claimId"));
        assertEquals("", support.safeParameter(stub.asRequest(), "missing"));
        assertEquals("unknown", support.currentOperator(stub.asRequest()));
        stub.sessionAttributes.put("user", "adjuster3");
        assertEquals("adjuster3", support.currentOperator(stub.asRequest()));
        support.rememberScreen(stub.asRequest(), "workbench");
        assertEquals("workbench", stub.sessionAttributes.get("lastScreen"));
        assertEquals("workbench", stub.attributes.get("screenName"));
    }

    @Test
    public void putClaimSummaryCopiesClaimFields() throws Exception {
        ServletStubs.Request stub = new ServletStubs.Request();
        support.putClaimSummary(stub.asRequest(), support.findClaim(1));
        assertEquals(Integer.valueOf(1), stub.attributes.get("claimId"));
        assertEquals("OPEN", stub.attributes.get("claimStatus"));
        assertEquals(Double.valueOf(1001), stub.attributes.get("reserveAmount"));
        assertEquals("adjuster1", stub.attributes.get("assignedAdjuster"));
        assertEquals("2019-01-15", stub.attributes.get("lossDate"));
        assertEquals("2019-03-15", stub.attributes.get("reportedDate"));
    }

    @Test
    public void putClaimSummaryHandlesMissingClaim() {
        ServletStubs.Request stub = new ServletStubs.Request();
        support.putClaimSummary(stub.asRequest(), support.findClaim(99999));
        assertEquals("UNKNOWN", stub.attributes.get("claimStatus"));
        assertEquals(Double.valueOf(0), stub.attributes.get("reserveAmount"));
        assertNull(stub.attributes.get("claimId"));
    }

    @Test
    public void lookupHelpersReadSeededRows() throws Exception {
        assertEquals(121, support.nextId("CLAIM"));
        assertEquals(120, support.countRows("CLAIM"));
        assertEquals("Claimant 1",
                support.selectString("select claimant_name from CLAIM where claim_id = ?", 1));
        assertEquals(1000.0,
                support.selectAmount("select policy_limit from POLICY where policy_id = ?", 9001),
                0.001);
        List statuses = support.selectStrings(
                "select distinct status from CLAIM order by status");
        assertEquals(Arrays.asList(new String[] {
            "APPROVED", "CLOSED", "DENIED", "INVESTIGATING", "OPEN" }), statuses);
    }

    @Test
    public void lookupHelpersReturnDefaultsWhenRowMissingOrQueryFails() {
        assertEquals("", support.selectString(
                "select claimant_name from CLAIM where claim_id = ?", 99999));
        assertEquals("", support.selectString("select nope from NOWHERE where id = ?", 1));
        assertEquals(0.0, support.selectAmount(
                "select policy_limit from POLICY where policy_id = ?", 99999), 0.001);
        assertEquals(0.0, support.selectAmount("select nope from NOWHERE where id = ?", 1),
                0.001);
        assertEquals(0, support.countRows("NO_SUCH_TABLE"));
        assertTrue(support.selectStrings("select nope from NOWHERE").isEmpty());
    }

    @Test
    public void updateReportsAffectedRows() throws Exception {
        String original = support.selectString(
                "select assigned_adjuster from CLAIM where claim_id = ?", 10);
        try {
            assertEquals(1, support.update(
                    "update CLAIM set assigned_adjuster = 'adjuster7' where claim_id = 10"));
            assertEquals("adjuster7", support.selectString(
                    "select assigned_adjuster from CLAIM where claim_id = ?", 10));
            assertEquals(0, support.update(
                    "update CLAIM set assigned_adjuster = 'x' where claim_id = 99999"));
        } finally {
            support.update("update CLAIM set assigned_adjuster = "
                    + support.quote(original) + " where claim_id = 10");
        }
    }
}
