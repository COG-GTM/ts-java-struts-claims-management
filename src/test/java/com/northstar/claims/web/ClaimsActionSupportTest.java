package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.junit.Test;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Policy;

/** Verifies the shared plumbing used by every claims screen action. */
public class ClaimsActionSupportTest {

    private final ClaimsActionSupport support = new ClaimsActionSupport() {
    };

    /** Minimal request and session stand-ins backed by plain maps. */
    private static final class FakeRequest {
        final Map parameters = new HashMap();
        final Map attributes = new HashMap();
        final Map sessionAttributes = new HashMap();

        HttpServletRequest proxy() {
            final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { HttpSession.class },
                    store(sessionAttributes));
            return (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { HttpServletRequest.class },
                    new InvocationHandler() {
                        public Object invoke(Object target, Method method,
                                Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("getParameter".equals(name)) {
                                return parameters.get(args[0]);
                            }
                            if ("getSession".equals(name)) {
                                return session;
                            }
                            return store(attributes).invoke(target, method, args);
                        }
                    });
        }

        private static InvocationHandler store(final Map values) {
            return new InvocationHandler() {
                public Object invoke(Object target, Method method, Object[] args) {
                    String name = method.getName();
                    if ("setAttribute".equals(name)) {
                        values.put(args[0], args[1]);
                        return null;
                    }
                    if ("getAttribute".equals(name)) {
                        return values.get(args[0]);
                    }
                    throw new UnsupportedOperationException(name);
                }
            };
        }
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
    public void normalizedDateConvertsDisplayToDatabaseFormat() {
        assertEquals("2019-03-15", support.normalizedDate("03/15/2019"));
        assertEquals("2020-01-01", support.normalizedDate("13/01/2019"));
    }

    @Test
    public void normalizedDateDefaultsBlankAndUnparseableInput() {
        assertEquals("2019-04-01", support.normalizedDate(null));
        assertEquals("2019-04-01", support.normalizedDate(""));
        assertEquals("2019-04-01", support.normalizedDate("tomorrow"));
    }

    @Test
    public void textHelpersTrimAndFallBack() {
        assertTrue(support.hasText(" x "));
        assertFalse(support.hasText("   "));
        assertFalse(support.hasText(null));
        assertEquals("abc", support.defaultText("  abc ", "z"));
        assertEquals("z", support.defaultText(" ", "z"));
    }

    @Test
    public void quoteEscapesApostrophesAndNulls() {
        assertEquals("null", support.quote(null));
        assertEquals("'O Brien'", support.quote("O'Brien"));
        assertEquals("''", support.quote(""));
    }

    @Test
    public void moneyUsesTwoDecimalsInUsLocale() {
        assertEquals("1234.50", support.money(1234.5));
        assertEquals("0.01", support.money(0.005));
    }

    @Test
    public void normalizersApplyScreenDefaults() {
        assertEquals("OPEN", support.normalizeStatus(null));
        assertEquals("CLOSED", support.normalizeStatus(" closed "));
        assertEquals("CHECK", support.normalizeMethod(""));
        assertEquals("EFT", support.normalizeMethod("eft"));
        assertEquals("WATER", support.normalizeLossType("  "));
        assertEquals("FIRE", support.normalizeLossType("Fire "));
    }

    @Test
    public void validDateShapeRequiresTwoTwoFourDigits() {
        assertTrue(support.validDateShape("03/15/2019"));
        assertFalse(support.validDateShape("3/15/2019"));
        assertFalse(support.validDateShape("2019-03-15"));
        assertFalse(support.validDateShape(null));
    }

    @Test
    public void statusClassificationIsCaseInsensitive() {
        assertTrue(support.approvedStatus("approved"));
        assertTrue(support.approvedStatus("CLOSED"));
        assertFalse(support.approvedStatus("OPEN"));
        assertFalse(support.approvedStatus(null));
        assertTrue(support.openStatus("open"));
        assertTrue(support.openStatus("Investigating"));
        assertFalse(support.openStatus("DENIED"));
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
        assertTrue(support.safeList(null).isEmpty());
        List values = new ArrayList();
        assertEquals(values, support.safeList(values));
        assertEquals("none", support.firstValue(null, "none"));
        assertEquals("none", support.firstValue(values, "none"));
        values.add(new Integer(5));
        assertEquals("5", support.firstValue(values, "none"));
        assertTrue(support.emptyList().isEmpty());
    }

    @Test
    public void labelsDescribeClaimsAndPolicies() {
        assertEquals("Unknown claim", support.claimLabel(null));
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-00001");
        claim.setClaimantName("Claimant 1");
        assertEquals("CLM-00001 - Claimant 1", support.claimLabel(claim));
        assertEquals("Unknown policy", support.policyLabel(null));
        Policy policy = new Policy();
        policy.setPolicyNumber("NS-00001");
        policy.setLineOfBusiness("AUTO");
        assertEquals("NS-00001 - AUTO", support.policyLabel(policy));
    }

    @Test
    public void putClaimSummaryPublishesClaimFields() {
        FakeRequest fake = new FakeRequest();
        Claim claim = new Claim();
        claim.setClaimId(12);
        claim.setStatus("OPEN");
        claim.setReserveAmount(2500);
        claim.setAssignedAdjuster("adjuster2");
        claim.setLossDate("2019-01-15");
        claim.setReportedDate("2019-02-15");
        support.putClaimSummary(fake.proxy(), claim);
        assertEquals(new Integer(12), fake.attributes.get("claimId"));
        assertEquals("OPEN", fake.attributes.get("claimStatus"));
        assertEquals(new Double(2500), fake.attributes.get("reserveAmount"));
        assertEquals("adjuster2", fake.attributes.get("assignedAdjuster"));
        assertEquals("2019-01-15", fake.attributes.get("lossDate"));
        assertEquals("2019-02-15", fake.attributes.get("reportedDate"));
    }

    @Test
    public void putClaimSummaryMarksMissingClaimUnknown() {
        FakeRequest fake = new FakeRequest();
        support.putClaimSummary(fake.proxy(), null);
        assertEquals("UNKNOWN", fake.attributes.get("claimStatus"));
        assertEquals(new Double(0), fake.attributes.get("reserveAmount"));
        assertFalse(fake.attributes.containsKey("claimId"));
    }

    @Test
    public void requestHelpersReadParametersAndSession() {
        FakeRequest fake = new FakeRequest();
        HttpServletRequest request = fake.proxy();
        assertEquals("unknown", support.currentOperator(request));
        fake.sessionAttributes.put("user", "adjuster1");
        assertEquals("adjuster1", support.currentOperator(request));
        fake.parameters.put("claimId", "  119 ");
        assertEquals("119", support.safeParameter(request, "claimId"));
        assertEquals("", support.safeParameter(request, "missing"));
        support.rememberScreen(request, "workbench");
        assertEquals("workbench", fake.sessionAttributes.get("lastScreen"));
        assertEquals("workbench", fake.attributes.get("screenName"));
    }

    @Test
    public void fixedOperatorAndReportDates() {
        assertEquals("2019-04-01", support.operatorDate());
        assertEquals("2019-04-01", support.reportDate());
    }
}
