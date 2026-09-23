package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;
import com.northstar.claims.web.PaymentAuthorization;
import com.northstar.claims.web.PaymentIssueAction;

/** Verifies that payment issuance is refused for unentitled operators. */
public class PaymentIssueAuthorizationTest {

    private static final int SETTLED_CLAIM = 119;

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/payment-auth");
        DatabaseBootstrap.bootstrap(true);
    }

    @Test
    public void refusesIssuanceByAnUnassignedAdjuster() throws Exception {
        int before = new PaymentDAO().findByClaim(SETTLED_CLAIM).size();
        Recorder recorder = issue("adjuster2", "POST", "9999999.00");
        assertEquals(403, recorder.status);
        assertEquals("REFUSED", recorder.attributes.get("paymentStatus"));
        assertEquals("payment.issue.denied", recorder.attributes.get("message"));
        assertEquals(before, new PaymentDAO().findByClaim(SETTLED_CLAIM).size());
    }

    @Test
    public void refusesIssuanceOnGetRequests() throws Exception {
        int before = new PaymentDAO().findByClaim(SETTLED_CLAIM).size();
        Recorder recorder = issue("supervisor", "GET", "100.00");
        assertEquals(403, recorder.status);
        assertEquals(before, new PaymentDAO().findByClaim(SETTLED_CLAIM).size());
    }

    @Test
    public void refusesNegativeAmounts() throws Exception {
        int before = new PaymentDAO().findByClaim(SETTLED_CLAIM).size();
        Recorder recorder = issue("supervisor", "POST", "-500.00");
        assertEquals(403, recorder.status);
        assertEquals("payment.issue.amount", recorder.attributes.get("message"));
        assertEquals(before, new PaymentDAO().findByClaim(SETTLED_CLAIM).size());
    }

    @Test
    public void allowsIssuanceBySupervisor() throws Exception {
        int before = new PaymentDAO().findByClaim(SETTLED_CLAIM).size();
        Recorder recorder = issue("supervisor", "POST", "1000.00");
        assertEquals(200, recorder.status);
        assertEquals("ISSUED", recorder.attributes.get("paymentStatus"));
        assertEquals(before + 1,
                new PaymentDAO().findByClaim(SETTLED_CLAIM).size());
    }

    @Test
    public void allowsIssuanceByTheAssignedAdjuster() throws Exception {
        Claim claim = new ClaimDAO().findById(SETTLED_CLAIM);
        int before = new PaymentDAO().findByClaim(SETTLED_CLAIM).size();
        Recorder recorder = issue(claim.getAssignedAdjuster(), "POST", "25.00");
        assertEquals(200, recorder.status);
        assertEquals(before + 1,
                new PaymentDAO().findByClaim(SETTLED_CLAIM).size());
    }

    @Test
    public void scopesRolesToTheirOwnClaims() throws Exception {
        Claim claim = new ClaimDAO().findById(SETTLED_CLAIM);
        assertTrue(PaymentAuthorization.canIssuePayment("supervisor",
                PaymentAuthorization.ROLE_SUPERVISOR, claim));
        assertTrue(PaymentAuthorization.canIssuePayment(
                claim.getAssignedAdjuster(),
                PaymentAuthorization.ROLE_ADJUSTER, claim));
        assertFalse(PaymentAuthorization.canIssuePayment("adjuster2",
                PaymentAuthorization.ROLE_ADJUSTER, claim));
        assertFalse(PaymentAuthorization.canIssuePayment("adjuster2",
                PaymentAuthorization.ROLE_NONE, claim));
        assertFalse(PaymentAuthorization.canIssuePayment("adjuster2",
                PaymentAuthorization.ROLE_ADJUSTER, null));
        assertEquals(PaymentAuthorization.ROLE_SUPERVISOR,
                PaymentAuthorization.roleFor("supervisor"));
        assertEquals(PaymentAuthorization.ROLE_ADJUSTER,
                PaymentAuthorization.roleFor("adjuster4"));
        assertEquals(PaymentAuthorization.ROLE_NONE,
                PaymentAuthorization.roleFor("guest"));
    }

    private Recorder issue(String operator, String method, String amount)
            throws Exception {
        Recorder recorder = new Recorder();
        Map parameters = new HashMap();
        parameters.put("claimId", String.valueOf(SETTLED_CLAIM));
        parameters.put("payeeName", "Attacker Payee");
        parameters.put("amount", amount);
        parameters.put("paymentMethod", "CHECK");
        Map session = new HashMap();
        session.put("user", operator);
        session.put("role", PaymentAuthorization.roleFor(operator));
        HttpServletRequest request = recorder.request(parameters, session,
                method);
        HttpServletResponse response = recorder.response();
        new PaymentIssueAction().execute(new AnyForwardMapping(), null,
                request, response);
        return recorder;
    }

    /** Resolves every forward name so the action can run outside Struts. */
    private static class AnyForwardMapping extends ActionMapping {
        public ActionForward findForward(String name) {
            return new ActionForward(name);
        }
    }

    /** Captures the servlet interactions performed by the action. */
    private static class Recorder {

        private final Map attributes = new HashMap();
        private int status = 200;

        HttpServletRequest request(final Map parameters, final Map session,
                final String method) {
            final HttpSession httpSession = (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { HttpSession.class },
                    new InvocationHandler() {
                        public Object invoke(Object proxy, Method call,
                                Object[] args) {
                            String name = call.getName();
                            if ("getAttribute".equals(name)) {
                                return session.get(args[0]);
                            }
                            if ("setAttribute".equals(name)) {
                                session.put(args[0], args[1]);
                            }
                            return null;
                        }
                    });
            return (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { HttpServletRequest.class },
                    new InvocationHandler() {
                        public Object invoke(Object proxy, Method call,
                                Object[] args) {
                            String name = call.getName();
                            if ("getParameter".equals(name)) {
                                return parameters.get(args[0]);
                            }
                            if ("getMethod".equals(name)) {
                                return method;
                            }
                            if ("getSession".equals(name)) {
                                return httpSession;
                            }
                            if ("setAttribute".equals(name)) {
                                attributes.put(args[0], args[1]);
                            }
                            if ("getAttribute".equals(name)) {
                                return attributes.get(args[0]);
                            }
                            return null;
                        }
                    });
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { HttpServletResponse.class },
                    new InvocationHandler() {
                        public Object invoke(Object proxy, Method call,
                                Object[] args) {
                            if ("setStatus".equals(call.getName())) {
                                status = ((Integer) args[0]).intValue();
                            }
                            return null;
                        }
                    });
        }
    }
}
