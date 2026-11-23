package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Policy;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies the shared parsing, normalization and JDBC plumbing used by screen actions. */
public class ClaimsActionSupportTest {

    private static class SupportAction extends ClaimsActionSupport {
    }

    private final SupportAction action = new SupportAction();
    private final Map requestAttributes = new HashMap();
    private final Map sessionAttributes = new HashMap();
    private final Map parameters = new HashMap();
    private HttpServletRequest request;

    @Before
    public void setUp() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        ConnectionPool.getInstance().closeAll();
        DatabaseBootstrap.bootstrap(true);
        final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] { HttpSession.class },
                new AttributeHandler(sessionAttributes, null));
        request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] { HttpServletRequest.class },
                new AttributeHandler(requestAttributes, session));
    }

    @AfterClass
    public static void releaseConnections() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    private class AttributeHandler implements InvocationHandler {
        private final Map attributes;
        private final HttpSession session;

        AttributeHandler(Map attributes, HttpSession session) {
            this.attributes = attributes;
            this.session = session;
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("setAttribute".equals(name)) {
                attributes.put(args[0], args[1]);
                return null;
            }
            if ("getAttribute".equals(name)) {
                return attributes.get(args[0]);
            }
            if ("getParameter".equals(name)) {
                return parameters.get(args[0]);
            }
            if ("getSession".equals(name)) {
                return session;
            }
            throw new UnsupportedOperationException(name);
        }
    }

    @Test
    public void integerAndDecimalFallBackOnBadInput() {
        assertEquals(42, action.integer("42", -1));
        assertEquals(-1, action.integer("4x", -1));
        assertEquals(7, action.integer(null, 7));
        assertEquals(12.5, action.decimal("12.5", 0), 0.0);
        assertEquals(3.0, action.decimal("abc", 3.0), 0.0);
        assertEquals(1.0, action.decimal(null, 1.0), 0.0);
    }

    @Test
    public void normalizedDateConvertsDisplayDateAndDefaults() {
        assertEquals("2019-03-15", action.normalizedDate("03/15/2019"));
        assertEquals("2019-04-01", action.normalizedDate(null));
        assertEquals("2019-04-01", action.normalizedDate(""));
        assertEquals("2019-04-01", action.normalizedDate("garbage"));
    }

    @Test
    public void normalizedDateIsLenientAboutOverflowingFields() {
        assertEquals("2020-01-15", action.normalizedDate("13/15/2019"));
    }

    @Test
    public void textHelpersTrimAndDefault() {
        assertTrue(action.hasText(" x "));
        assertFalse(action.hasText("   "));
        assertFalse(action.hasText(null));
        assertEquals("abc", action.defaultText("  abc  ", "fallback"));
        assertEquals("fallback", action.defaultText(" ", "fallback"));
        assertEquals("fallback", action.defaultText(null, "fallback"));
    }

    @Test
    public void quoteWrapsValueAndStripsSingleQuotes() {
        assertEquals("null", action.quote(null));
        assertEquals("'abc'", action.quote("abc"));
        assertEquals("'O Brien'", action.quote("O'Brien"));
    }

    @Test
    public void moneyFormatsTwoDecimalsInUsLocale() {
        assertEquals("1234.50", action.money(1234.5));
        assertEquals("0.01", action.money(0.005));
        assertEquals("-2.00", action.money(-2));
    }

    @Test
    public void normalizersUppercaseOrApplyDefaults() {
        assertEquals("OPEN", action.normalizeStatus(null));
        assertEquals("CLOSED", action.normalizeStatus(" closed "));
        assertEquals("CHECK", action.normalizeMethod(""));
        assertEquals("EFT", action.normalizeMethod("eft"));
        assertEquals("WATER", action.normalizeLossType("  "));
        assertEquals("FIRE", action.normalizeLossType(" fire"));
    }

    @Test
    public void statusPredicatesAreCaseInsensitive() {
        assertTrue(action.approvedStatus("approved"));
        assertTrue(action.approvedStatus("CLOSED"));
        assertFalse(action.approvedStatus("OPEN"));
        assertFalse(action.approvedStatus(null));
        assertTrue(action.openStatus("open"));
        assertTrue(action.openStatus("Investigating"));
        assertFalse(action.openStatus("DENIED"));
        assertFalse(action.openStatus(null));
    }

    @Test
    public void validDateShapeRequiresTwoTwoFourDigits() {
        assertTrue(action.validDateShape("03/15/2019"));
        assertFalse(action.validDateShape("3/15/2019"));
        assertFalse(action.validDateShape("2019-03-15"));
        assertFalse(action.validDateShape(null));
    }

    @Test
    public void financialAmountAcceptsZeroUpToOneHundredMillionExclusive() {
        assertTrue(action.financialAmount(0));
        assertTrue(action.financialAmount(99999999.99));
        assertFalse(action.financialAmount(100000000.0));
        assertFalse(action.financialAmount(-0.01));
    }

    @Test
    public void listHelpersHandleNullAndEmpty() {
        assertTrue(action.safeList(null).isEmpty());
        List values = new ArrayList(Arrays.asList(new String[] { "a", "b" }));
        assertSame(values, action.safeList(values));
        assertEquals("a", action.firstValue(values, "none"));
        assertEquals("none", action.firstValue(new ArrayList(), "none"));
        assertEquals("none", action.firstValue(null, "none"));
        assertTrue(action.emptyList().isEmpty());
    }

    @Test
    public void labelsDescribeClaimAndPolicyOrUnknown() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-00001");
        claim.setClaimantName("Claimant 1");
        assertEquals("CLM-00001 - Claimant 1", action.claimLabel(claim));
        assertEquals("Unknown claim", action.claimLabel(null));
        Policy policy = new Policy();
        policy.setPolicyNumber("NS-00001");
        policy.setLineOfBusiness("AUTO");
        assertEquals("NS-00001 - AUTO", action.policyLabel(policy));
        assertEquals("Unknown policy", action.policyLabel(null));
    }

    @Test
    public void fixedOperatorAndReportDates() {
        assertEquals("2019-04-01", action.operatorDate());
        assertEquals("2019-04-01", action.reportDate());
    }

    @Test
    public void putClaimSummaryPublishesClaimFields() {
        Claim claim = new Claim();
        claim.setClaimId(9);
        claim.setStatus("OPEN");
        claim.setReserveAmount(1500);
        claim.setAssignedAdjuster("adjuster1");
        claim.setLossDate("2019-01-15");
        claim.setReportedDate("2019-03-15");
        action.putClaimSummary(request, claim);
        assertEquals(new Integer(9), requestAttributes.get("claimId"));
        assertEquals("OPEN", requestAttributes.get("claimStatus"));
        assertEquals(new Double(1500), requestAttributes.get("reserveAmount"));
        assertEquals("adjuster1", requestAttributes.get("assignedAdjuster"));
        assertEquals("2019-01-15", requestAttributes.get("lossDate"));
        assertEquals("2019-03-15", requestAttributes.get("reportedDate"));
    }

    @Test
    public void putClaimSummaryMarksMissingClaimUnknown() {
        action.putClaimSummary(request, null);
        assertEquals("UNKNOWN", requestAttributes.get("claimStatus"));
        assertEquals(new Double(0), requestAttributes.get("reserveAmount"));
        assertNull(requestAttributes.get("claimId"));
    }

    @Test
    public void currentOperatorReadsSessionUser() {
        assertEquals("unknown", action.currentOperator(request));
        sessionAttributes.put("user", "supervisor");
        assertEquals("supervisor", action.currentOperator(request));
    }

    @Test
    public void safeParameterTrimsAndDefaultsToEmpty() {
        parameters.put("claimId", "  12 ");
        assertEquals("12", action.safeParameter(request, "claimId"));
        assertEquals("", action.safeParameter(request, "missing"));
    }

    @Test
    public void rememberScreenStoresOnSessionAndRequest() {
        action.rememberScreen(request, "workbench");
        assertEquals("workbench", sessionAttributes.get("lastScreen"));
        assertEquals("workbench", requestAttributes.get("screenName"));
    }

    @Test
    public void findClaimLoadsSeededClaimOrNull() {
        Claim claim = action.findClaim(1);
        assertEquals("CLM-00001", claim.getClaimNumber());
        assertNull(action.findClaim(987654));
    }

    @Test
    public void nextIdIsOneAboveCurrentMaximum() throws Exception {
        assertEquals(121, action.nextId("CLAIM"));
        assertEquals(61, action.nextId("PAYMENT"));
        assertEquals(7, action.nextId("ADJUSTER"));
    }

    @Test
    public void updateExecutesStatementAndReturnsRowCount() throws Exception {
        assertEquals(1, action.update("update CLAIM set status = 'CLOSED' where claim_id = 1"));
        assertEquals("CLOSED", action.selectString(
                "select status from CLAIM where claim_id = ?", 1));
        assertEquals(0, action.update("update CLAIM set status = 'CLOSED' where claim_id = -1"));
    }

    @Test
    public void scalarLookupsReturnValuesOrDefaults() {
        assertEquals("Claimant 2", action.selectString(
                "select claimant_name from CLAIM where claim_id = ?", 2));
        assertEquals("", action.selectString(
                "select claimant_name from CLAIM where claim_id = ?", -1));
        assertEquals("", action.selectString("select broken sql ?", 1));
        assertEquals(1002.0, action.selectAmount(
                "select reserve_amount from CLAIM where claim_id = ?", 2), 0.0);
        assertEquals(0.0, action.selectAmount(
                "select reserve_amount from CLAIM where claim_id = ?", -1), 0.0);
        assertEquals(0.0, action.selectAmount("select broken sql ?", 1), 0.0);
    }

    @Test
    public void selectStringsReturnsColumnValuesOrEmptyOnFailure() {
        List names = action.selectStrings(
                "select username from ADJUSTER order by adjuster_id");
        assertEquals(6, names.size());
        assertEquals("adjuster1", names.get(0));
        assertEquals("supervisor", names.get(5));
        assertTrue(action.selectStrings("select nothing from NO_TABLE").isEmpty());
    }

    @Test
    public void countRowsReturnsTableSizeOrZeroOnFailure() {
        assertEquals(42, action.countRows("POLICY"));
        assertEquals(120, action.countRows("CLAIM"));
        assertEquals(0, action.countRows("NO_SUCH_TABLE"));
    }
}
