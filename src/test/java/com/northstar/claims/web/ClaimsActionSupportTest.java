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
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Policy;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies the shared helpers every Struts action inherits. */
public class ClaimsActionSupportTest {

    private final ClaimsActionSupport support = new ClaimsActionSupport() {
    };

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        ConnectionPool.getInstance().closeAll();
        DatabaseBootstrap.bootstrap(true);
    }

    @AfterClass
    public static void releaseConnections() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    /** Map-backed stand-ins for the servlet request and session. */
    private static final class FakeWeb {
        final Map parameters = new HashMap();
        final Map requestAttributes = new HashMap();
        final Map sessionAttributes = new HashMap();
        final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(), new Class[] {HttpSession.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        return dispatch(sessionAttributes, method, args);
                    }
                });
        final HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[] {HttpServletRequest.class }, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getSession".equals(method.getName())) {
                            return session;
                        }
                        if ("getParameter".equals(method.getName())) {
                            return parameters.get(args[0]);
                        }
                        return dispatch(requestAttributes, method, args);
                    }
                });

        private static Object dispatch(Map attributes, Method method, Object[] args) {
            if ("setAttribute".equals(method.getName())) {
                attributes.put(args[0], args[1]);
                return null;
            }
            if ("getAttribute".equals(method.getName())) {
                return attributes.get(args[0]);
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }

    @Test
    public void integerAndDecimalFallBackOnBadInput() {
        assertEquals(42, support.integer("42", -1));
        assertEquals(-1, support.integer("4.2", -1));
        assertEquals(-1, support.integer(null, -1));
        assertEquals(12.5, support.decimal("12.5", 0), 0.0);
        assertEquals(7.0, support.decimal("twelve", 7), 0.0);
        assertEquals(7.0, support.decimal(null, 7), 0.0);
    }

    @Test
    public void normalizedDateConvertsDisplayDatesAndDefaults() {
        assertEquals("2019-03-15", support.normalizedDate("03/15/2019"));
        assertEquals("2019-04-01", support.normalizedDate(null));
        assertEquals("2019-04-01", support.normalizedDate(""));
        assertEquals("2019-04-01", support.normalizedDate("2019-03-15x"));
        assertEquals("2020-01-01", support.normalizedDate("13/01/2019"));
    }

    @Test
    public void textHelpersTrimAndDefault() {
        assertFalse(support.hasText(null));
        assertFalse(support.hasText("  "));
        assertTrue(support.hasText(" x "));
        assertEquals("value", support.defaultText("  value ", "fallback"));
        assertEquals("fallback", support.defaultText(" ", "fallback"));
        assertEquals("fallback", support.defaultText(null, "fallback"));
    }

    @Test
    public void quoteWrapsValueAndStripsEmbeddedQuotes() {
        assertEquals("null", support.quote(null));
        assertEquals("'abc'", support.quote("abc"));
        assertEquals("'O Brien'", support.quote("O'Brien"));
    }

    @Test
    public void moneyUsesTwoDecimalsAndFixedDatesAreAsOfDate() {
        assertEquals("1234.50", support.money(1234.5));
        assertEquals("-3.00", support.money(-3));
        assertEquals("2019-04-01", support.operatorDate());
        assertEquals("2019-04-01", support.reportDate());
    }

    @Test
    public void listHelpersHandleNullAndEmpty() {
        List values = new ArrayList(Arrays.asList(new Object[] {new Integer(5), "b" }));
        assertSame(values, support.safeList(values));
        assertTrue(support.safeList(null).isEmpty());
        assertTrue(support.emptyList().isEmpty());
        assertEquals("5", support.firstValue(values, "none"));
        assertEquals("none", support.firstValue(new ArrayList(), "none"));
        assertEquals("none", support.firstValue(null, "none"));
    }

    @Test
    public void normalizersUppercaseAndApplyDefaults() {
        assertEquals("INVESTIGATING", support.normalizeStatus(" investigating "));
        assertEquals("OPEN", support.normalizeStatus(null));
        assertEquals("EFT", support.normalizeMethod("eft"));
        assertEquals("CHECK", support.normalizeMethod(" "));
        assertEquals("FIRE", support.normalizeLossType(" fire"));
        assertEquals("WATER", support.normalizeLossType(""));
    }

    @Test
    public void validDateShapeRequiresTwoTwoFourDigits() {
        assertTrue(support.validDateShape("03/15/2019"));
        assertFalse(support.validDateShape("3/15/2019"));
        assertFalse(support.validDateShape("2019-03-15"));
        assertFalse(support.validDateShape(null));
    }

    @Test
    public void statusPredicatesAreCaseInsensitive() {
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
        assertTrue(support.financialAmount(0));
        assertTrue(support.financialAmount(99999999.99));
        assertFalse(support.financialAmount(100000000.0));
        assertFalse(support.financialAmount(-0.01));
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
    public void putClaimSummaryPublishesClaimFields() {
        FakeWeb web = new FakeWeb();
        Claim claim = new Claim();
        claim.setClaimId(9);
        claim.setStatus("OPEN");
        claim.setReserveAmount(250.5);
        claim.setAssignedAdjuster("adjuster1");
        claim.setLossDate("2019-01-15");
        claim.setReportedDate("2019-02-15");
        support.putClaimSummary(web.request, claim);
        assertEquals(new Integer(9), web.requestAttributes.get("claimId"));
        assertEquals("OPEN", web.requestAttributes.get("claimStatus"));
        assertEquals(new Double(250.5), web.requestAttributes.get("reserveAmount"));
        assertEquals("adjuster1", web.requestAttributes.get("assignedAdjuster"));
        assertEquals("2019-01-15", web.requestAttributes.get("lossDate"));
        assertEquals("2019-02-15", web.requestAttributes.get("reportedDate"));
    }

    @Test
    public void putClaimSummaryForMissingClaimUsesUnknownDefaults() {
        FakeWeb web = new FakeWeb();
        support.putClaimSummary(web.request, null);
        assertEquals("UNKNOWN", web.requestAttributes.get("claimStatus"));
        assertEquals(new Double(0), web.requestAttributes.get("reserveAmount"));
        assertEquals(2, web.requestAttributes.size());
    }

    @Test
    public void sessionAndParameterHelpers() {
        FakeWeb web = new FakeWeb();
        assertEquals("unknown", support.currentOperator(web.request));
        web.sessionAttributes.put("user", "supervisor");
        assertEquals("supervisor", support.currentOperator(web.request));
        web.parameters.put("claimId", "  119 ");
        assertEquals("119", support.safeParameter(web.request, "claimId"));
        assertEquals("", support.safeParameter(web.request, "missing"));
        support.rememberScreen(web.request, "workbench");
        assertEquals("workbench", web.sessionAttributes.get("lastScreen"));
        assertEquals("workbench", web.requestAttributes.get("screenName"));
    }

    @Test
    public void databaseLookupsReadSeededRows() throws Exception {
        assertEquals(121, support.nextId("CLAIM"));
        assertEquals(61, support.nextId("PAYMENT"));
        assertEquals(42, support.countRows("POLICY"));
        assertEquals("CLM-00002",
                support.selectString("select claim_number from CLAIM where claim_id = ?", 2));
        assertEquals("",
                support.selectString("select claim_number from CLAIM where claim_id = ?", 987654));
        assertEquals(250.0,
                support.selectAmount("select amount from PAYMENT where claim_id = ?", 2), 0.0);
        assertEquals(0.0,
                support.selectAmount("select amount from PAYMENT where claim_id = ?", 987654), 0.0);
        List statuses = support.selectStrings("select distinct status from CLAIM order by status");
        assertEquals(Arrays.asList(new String[] {"APPROVED", "CLOSED", "DENIED",
            "INVESTIGATING", "OPEN" }), statuses);
    }

    @Test
    public void databaseLookupsSwallowSqlErrors() {
        assertEquals(0, support.countRows("NO_SUCH_TABLE"));
        assertEquals("", support.selectString("select nope from NO_SUCH_TABLE where id = ?", 1));
        assertEquals(0.0, support.selectAmount("select nope from NO_SUCH_TABLE where id = ?", 1), 0.0);
        assertTrue(support.selectStrings("select nope from NO_SUCH_TABLE").isEmpty());
    }

    @Test
    public void updateReturnsAffectedRowCount() throws Exception {
        assertEquals(1, support.update("update CLAIM set assigned_adjuster = 'adjuster9'"
                + " where claim_id = 7"));
        assertEquals("adjuster9", support.selectString(
                "select assigned_adjuster from CLAIM where claim_id = ?", 7));
        assertEquals(0, support.update("update CLAIM set status = 'OPEN' where claim_id = 987654"));
    }

    @Test
    public void findClaimReturnsClaimOrNull() {
        Claim claim = support.findClaim(3);
        assertEquals("CLM-00003", claim.getClaimNumber());
        assertEquals("APPROVED", claim.getStatus());
        assertNull(support.findClaim(987654));
    }
}
