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

/** Verifies the shared plumbing used by every Struts screen action. */
public class ClaimsActionSupportTest {

    private static class Support extends ClaimsActionSupport {
    }

    /** Minimal in-memory request/session backed by dynamic proxies. */
    private static class FakeRequest implements InvocationHandler {
        final Map parameters = new HashMap();
        final Map attributes = new HashMap();
        final Map sessionAttributes = new HashMap();
        final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] { HttpSession.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("getAttribute")) {
                            return sessionAttributes.get(args[0]);
                        }
                        if (method.getName().equals("setAttribute")) {
                            sessionAttributes.put(args[0], args[1]);
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
                });

        HttpServletRequest proxy() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { HttpServletRequest.class }, this);
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("getParameter")) {
                return parameters.get(args[0]);
            }
            if (name.equals("getAttribute")) {
                return attributes.get(args[0]);
            }
            if (name.equals("setAttribute")) {
                attributes.put(args[0], args[1]);
                return null;
            }
            if (name.equals("getSession")) {
                return session;
            }
            throw new UnsupportedOperationException(name);
        }
    }

    private final Support support = new Support();

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
        ConnectionPool.getInstance().closeAll();
    }

    @AfterClass
    public static void releasePool() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    @Test
    public void integerAndDecimalFallBackOnBadInput() {
        assertEquals(42, support.integer("42", -1));
        assertEquals(-1, support.integer("4.2", -1));
        assertEquals(-1, support.integer(null, -1));
        assertEquals(12.5, support.decimal("12.5", -1), 0.0);
        assertEquals(-1, support.decimal("abc", -1), 0.0);
        assertEquals(-1, support.decimal(null, -1), 0.0);
    }

    @Test
    public void normalizedDateConvertsDisplayDatesToDatabaseDates() {
        assertEquals("2019-03-15", support.normalizedDate("03/15/2019"));
    }

    @Test
    public void normalizedDateRollsOverOutOfRangeValuesLeniently() {
        // Matches the intake_lenient_date transcript: legacy lenient parsing.
        assertEquals("2020-02-14", support.normalizedDate("13/45/2019"));
    }

    @Test
    public void normalizedDateDefaultsToOperatorDateForBlankOrUnparseable() {
        assertEquals("2019-04-01", support.normalizedDate(null));
        assertEquals("2019-04-01", support.normalizedDate(""));
        assertEquals("2019-04-01", support.normalizedDate("yesterday"));
    }

    @Test
    public void textHelpers() {
        assertTrue(support.hasText(" x "));
        assertFalse(support.hasText("   "));
        assertFalse(support.hasText(null));
        assertEquals("value", support.defaultText("  value  ", "fallback"));
        assertEquals("fallback", support.defaultText(" ", "fallback"));
        assertEquals("fallback", support.defaultText(null, "fallback"));
    }

    @Test
    public void quoteStripsSingleQuotesForConcatenatedSql() {
        assertEquals("null", support.quote(null));
        assertEquals("'abc'", support.quote("abc"));
        assertEquals("'O Brien'", support.quote("O'Brien"));
    }

    @Test
    public void moneyAlwaysUsesUsFormatting() {
        assertEquals("1234.50", support.money(1234.5));
        assertEquals("0.00", support.money(0));
    }

    @Test
    public void normalizersTrimUppercaseAndDefault() {
        assertEquals("OPEN", support.normalizeStatus(null));
        assertEquals("CLOSED", support.normalizeStatus(" closed "));
        assertEquals("CHECK", support.normalizeMethod(""));
        assertEquals("EFT", support.normalizeMethod("eft"));
        assertEquals("WATER", support.normalizeLossType("   "));
        assertEquals("FIRE", support.normalizeLossType(" Fire"));
    }

    @Test
    public void validDateShapeRequiresTwoTwoFourDigits() {
        assertTrue(support.validDateShape("03/15/2019"));
        assertTrue(support.validDateShape("13/45/2019"));
        assertFalse(support.validDateShape("3/15/2019"));
        assertFalse(support.validDateShape("2019-03-15"));
        assertFalse(support.validDateShape(null));
    }

    @Test
    public void fixedReportAndOperatorDates() {
        assertEquals("2019-04-01", support.reportDate());
        assertEquals("2019-04-01", support.operatorDate());
    }

    @Test
    public void statusClassification() {
        assertTrue(support.approvedStatus("approved"));
        assertTrue(support.approvedStatus("CLOSED"));
        assertFalse(support.approvedStatus("OPEN"));
        assertFalse(support.approvedStatus(null));
        assertTrue(support.openStatus("open"));
        assertTrue(support.openStatus("INVESTIGATING"));
        assertFalse(support.openStatus("DENIED"));
        assertFalse(support.openStatus(null));
    }

    @Test
    public void financialAmountBounds() {
        assertTrue(support.financialAmount(0.0));
        assertTrue(support.financialAmount(99999999.99));
        assertFalse(support.financialAmount(100000000.0));
        assertFalse(support.financialAmount(-0.01));
    }

    @Test
    public void listHelpers() {
        List empty = support.emptyList();
        assertTrue(empty.isEmpty());
        assertTrue(support.safeList(null).isEmpty());
        List values = new ArrayList(Arrays.asList(new Object[] { "a", "b" }));
        assertSame(values, support.safeList(values));
        assertEquals("a", support.firstValue(values, "none"));
        assertEquals("none", support.firstValue(new ArrayList(), "none"));
        assertEquals("none", support.firstValue(null, "none"));
    }

    @Test
    public void labelsHandleMissingEntities() {
        assertEquals("Unknown claim", support.claimLabel(null));
        assertEquals("Unknown policy", support.policyLabel(null));
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-00001");
        claim.setClaimantName("Claimant 1");
        assertEquals("CLM-00001 - Claimant 1", support.claimLabel(claim));
        Policy policy = new Policy();
        policy.setPolicyNumber("POL-1");
        policy.setLineOfBusiness("HOME");
        assertEquals("POL-1 - HOME", support.policyLabel(policy));
    }

    @Test
    public void safeParameterTrimsAndDefaultsToEmpty() {
        FakeRequest fake = new FakeRequest();
        fake.parameters.put("claimId", "  42 ");
        HttpServletRequest request = fake.proxy();
        assertEquals("42", support.safeParameter(request, "claimId"));
        assertEquals("", support.safeParameter(request, "missing"));
    }

    @Test
    public void rememberScreenWritesSessionAndRequest() {
        FakeRequest fake = new FakeRequest();
        support.rememberScreen(fake.proxy(), "workbench.view");
        assertEquals("workbench.view", fake.sessionAttributes.get("lastScreen"));
        assertEquals("workbench.view", fake.attributes.get("screenName"));
    }

    @Test
    public void currentOperatorReadsSessionUser() {
        FakeRequest fake = new FakeRequest();
        assertEquals("unknown", support.currentOperator(fake.proxy()));
        fake.sessionAttributes.put("user", "supervisor");
        assertEquals("supervisor", support.currentOperator(fake.proxy()));
    }

    @Test
    public void putClaimSummaryCopiesClaimFields() {
        FakeRequest fake = new FakeRequest();
        Claim claim = new Claim();
        claim.setClaimId(7);
        claim.setStatus("OPEN");
        claim.setReserveAmount(1500.5);
        claim.setAssignedAdjuster("adjuster1");
        claim.setLossDate("2019-01-15");
        claim.setReportedDate("2019-03-15");
        support.putClaimSummary(fake.proxy(), claim);
        assertEquals(new Integer(7), fake.attributes.get("claimId"));
        assertEquals("OPEN", fake.attributes.get("claimStatus"));
        assertEquals(new Double(1500.5), fake.attributes.get("reserveAmount"));
        assertEquals("adjuster1", fake.attributes.get("assignedAdjuster"));
        assertEquals("2019-01-15", fake.attributes.get("lossDate"));
        assertEquals("2019-03-15", fake.attributes.get("reportedDate"));
    }

    @Test
    public void putClaimSummaryMarksMissingClaimUnknown() {
        FakeRequest fake = new FakeRequest();
        support.putClaimSummary(fake.proxy(), null);
        assertEquals("UNKNOWN", fake.attributes.get("claimStatus"));
        assertEquals(new Double(0), fake.attributes.get("reserveAmount"));
        assertNull(fake.attributes.get("claimId"));
    }

    @Test
    public void findClaimLoadsSeededClaimOrNull() {
        Claim claim = support.findClaim(1);
        assertEquals("CLM-00001", claim.getClaimNumber());
        assertNull(support.findClaim(99999));
    }

    @Test
    public void scalarLookups() {
        assertEquals("Claimant 1", support.selectString(
                "select claimant_name from CLAIM where claim_id = ?", 1));
        assertEquals("", support.selectString(
                "select claimant_name from CLAIM where claim_id = ?", 99999));
        assertEquals(1001.0, support.selectAmount(
                "select reserve_amount from CLAIM where claim_id = ?", 1), 0.0);
        assertEquals(0.0, support.selectAmount(
                "select reserve_amount from CLAIM where claim_id = ?", 99999), 0.0);
    }

    @Test
    public void lookupsSwallowSqlErrors() {
        assertEquals("", support.selectString("select nope from NO_TABLE where id = ?", 1));
        assertEquals(0.0, support.selectAmount("select nope from NO_TABLE where id = ?", 1), 0.0);
        assertTrue(support.selectStrings("select nope from NO_TABLE").isEmpty());
        assertEquals(0, support.countRows("NO_TABLE"));
    }

    @Test
    public void listAndCountLookups() {
        List names = support.selectStrings(
                "select username from ADJUSTER order by adjuster_id");
        assertEquals(6, names.size());
        assertEquals("adjuster1", names.get(0));
        assertEquals(42, support.countRows("POLICY"));
        assertEquals(120, support.countRows("CLAIM"));
    }

    @Test
    public void nextIdIsOneMoreThanCurrentMaximum() throws Exception {
        assertEquals(121, support.nextId("CLAIM"));
        assertEquals(7, support.nextId("ADJUSTER"));
    }

    @Test
    public void updateReturnsAffectedRowCount() throws Exception {
        assertEquals(1, support.update(
                "update CLAIM set description = 'Updated by test' where claim_id = 120"));
        assertEquals("Updated by test", support.selectString(
                "select description from CLAIM where claim_id = ?", 120));
        assertEquals(0, support.update(
                "update CLAIM set description = 'x' where claim_id = 99999"));
    }

    @Test(expected = Exception.class)
    public void nextIdPropagatesSqlErrors() throws Exception {
        support.nextId("NO_TABLE");
    }
}
