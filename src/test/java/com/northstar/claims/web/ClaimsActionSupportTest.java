package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.TestDatabase;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Policy;

/** Covers the shared parsing, normalization, and JDBC helpers used by actions. */
public class ClaimsActionSupportTest {

    private final ClaimsActionSupport support = new ClaimsActionSupport() {
    };

    @BeforeClass
    public static void seed() throws Exception {
        TestDatabase.reset();
    }

    @AfterClass
    public static void release() throws Exception {
        TestDatabase.release();
    }

    @Test
    public void integerAndDecimalFallBackOnBadInput() {
        assertEquals(42, support.integer("42", 7));
        assertEquals(7, support.integer("4x", 7));
        assertEquals(7, support.integer(null, 7));
        assertEquals(12.5, support.decimal("12.5", 1), 0.0);
        assertEquals(1.0, support.decimal("", 1), 0.0);
        assertEquals(1.0, support.decimal(null, 1), 0.0);
    }

    @Test
    public void normalizedDateConvertsBrowserDatesAndDefaultsBlankOrBadInput() {
        assertEquals("2019-03-15", support.normalizedDate("03/15/2019"));
        assertEquals("2019-04-01", support.normalizedDate(null));
        assertEquals("2019-04-01", support.normalizedDate(""));
        assertEquals("2019-04-01", support.normalizedDate("yesterday"));
    }

    @Test
    public void textHelpersTrimAndDefault() {
        assertTrue(support.hasText(" a "));
        assertFalse(support.hasText("   "));
        assertFalse(support.hasText(null));
        assertEquals("value", support.defaultText("  value ", "fallback"));
        assertEquals("fallback", support.defaultText(" ", "fallback"));
        assertEquals("null", support.quote(null));
        assertEquals("'O Brien'", support.quote("O'Brien"));
    }

    @Test
    public void normalizersUppercaseAndApplyWorkflowDefaults() {
        assertEquals("OPEN", support.normalizeStatus(null));
        assertEquals("CLOSED", support.normalizeStatus(" closed "));
        assertEquals("CHECK", support.normalizeMethod(""));
        assertEquals("EFT", support.normalizeMethod("eft"));
        assertEquals("WATER", support.normalizeLossType(null));
        assertEquals("FIRE", support.normalizeLossType(" fire"));
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
    }

    @Test
    public void validDateShapeRequiresTwoTwoFourDigits() {
        assertTrue(support.validDateShape("03/15/2019"));
        assertFalse(support.validDateShape("3/15/2019"));
        assertFalse(support.validDateShape("2019-03-15"));
        assertFalse(support.validDateShape(null));
    }

    @Test
    public void financialAmountAcceptsZeroUpToOneHundredMillionExclusive() {
        assertTrue(support.financialAmount(0.0));
        assertTrue(support.financialAmount(99999999.99));
        assertFalse(support.financialAmount(100000000.0));
        assertFalse(support.financialAmount(-0.01));
    }

    @Test
    public void moneyFormatsTwoDecimalsWithUsSeparator() {
        assertEquals("1234.50", support.money(1234.5));
        assertEquals("0.00", support.money(0));
    }

    @Test
    public void listHelpersHandleNullAndEmptyLists() {
        assertTrue(support.safeList(null).isEmpty());
        List values = new ArrayList(Arrays.asList(new String[] { "a", "b" }));
        assertSame(values, support.safeList(values));
        assertEquals("a", support.firstValue(values, "none"));
        assertEquals("none", support.firstValue(new ArrayList(), "none"));
        assertEquals("none", support.firstValue(null, "none"));
        assertTrue(support.emptyList().isEmpty());
    }

    @Test
    public void labelsDescribeClaimsAndPolicies() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-00007");
        claim.setClaimantName("Pat Lee");
        assertEquals("CLM-00007 - Pat Lee", support.claimLabel(claim));
        assertEquals("Unknown claim", support.claimLabel(null));
        Policy policy = new Policy();
        policy.setPolicyNumber("NS-00001");
        policy.setLineOfBusiness("AUTO");
        assertEquals("NS-00001 - AUTO", support.policyLabel(policy));
        assertEquals("Unknown policy", support.policyLabel(null));
    }

    @Test
    public void requestHelpersReadParametersAndSession() {
        RequestStub stub = new RequestStub().param("claimId", "  12 ");
        HttpServletRequest request = stub.request();
        assertEquals("12", support.safeParameter(request, "claimId"));
        assertEquals("", support.safeParameter(request, "missing"));
        assertEquals("unknown", support.currentOperator(request));
        stub.sessionAttributes.put("user", "adjuster2");
        assertEquals("adjuster2", support.currentOperator(request));
        support.rememberScreen(request, "workbench");
        assertEquals("workbench", stub.sessionAttributes.get("lastScreen"));
        assertEquals("workbench", stub.attributes.get("screenName"));
    }

    @Test
    public void putClaimSummaryCopiesClaimFieldsOrMarksUnknown() {
        RequestStub missing = new RequestStub();
        support.putClaimSummary(missing.request(), null);
        assertEquals("UNKNOWN", missing.attributes.get("claimStatus"));
        assertEquals(new Double(0), missing.attributes.get("reserveAmount"));
        assertNull(missing.attributes.get("claimId"));

        RequestStub found = new RequestStub();
        support.putClaimSummary(found.request(), support.findClaim(2));
        assertEquals(new Integer(2), found.attributes.get("claimId"));
        assertEquals("INVESTIGATING", found.attributes.get("claimStatus"));
        assertEquals(new Double(1002), found.attributes.get("reserveAmount"));
        assertEquals("adjuster2", found.attributes.get("assignedAdjuster"));
        assertEquals("2019-01-15", found.attributes.get("lossDate"));
        assertEquals("2019-02-15", found.attributes.get("reportedDate"));
    }

    @Test
    public void findClaimReturnsNullForUnknownId() {
        assertEquals("CLM-00001", support.findClaim(1).getClaimNumber());
        assertNull(support.findClaim(99999));
    }

    @Test
    public void scalarQueriesReturnValuesOrSafeDefaults() {
        assertEquals("NS-09001", support.selectString(
                "select policy_number from POLICY where policy_id = ?", 9001));
        assertEquals("", support.selectString(
                "select policy_number from POLICY where policy_id = ?", -1));
        assertEquals("", support.selectString("select from nowhere", 1));
        assertEquals(1000.0, support.selectAmount(
                "select policy_limit from POLICY where policy_id = ?", 9001), 0.0);
        assertEquals(0.0, support.selectAmount(
                "select policy_limit from POLICY where policy_id = ?", -1), 0.0);
        assertEquals(0.0, support.selectAmount("select from nowhere", 1), 0.0);
    }

    @Test
    public void listAndCountQueriesSwallowFailures() {
        List lines = support.selectStrings(
                "select distinct line_of_business from POLICY order by 1");
        assertEquals(Arrays.asList(new String[] { "AUTO",
                "COMMERCIAL_PROPERTY", "GENERAL_LIABILITY", "HOMEOWNERS" }), lines);
        assertTrue(support.selectStrings("select from nowhere").isEmpty());
        assertEquals(6, support.countRows("ADJUSTER"));
        assertEquals(0, support.countRows("NO_SUCH_TABLE"));
    }

    @Test
    public void nextIdAndUpdateUseLiveTables() throws Exception {
        assertEquals(7, support.nextId("ADJUSTER"));
        assertEquals(1, support.update(
                "update ADJUSTER set region = 'NORTHEAST' where adjuster_id = 1"));
        assertEquals("NORTHEAST", support.selectString(
                "select region from ADJUSTER where adjuster_id = ?", 1));
        assertEquals(0, support.update(
                "update ADJUSTER set region = 'X' where adjuster_id = -1"));
    }

    @Test
    public void fixedOperatorAndReportDates() {
        assertEquals("2019-04-01", support.operatorDate());
        assertEquals("2019-04-01", support.reportDate());
    }
}
