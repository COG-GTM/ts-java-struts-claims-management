package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies that operator input reaches SQL as a bound value, never as syntax. */
public class SqlInjectionTest {

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
        ConnectionPool.getInstance().closeAll();
    }

    @AfterClass
    public static void releasePooledConnections() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    @Test
    public void policySearchFilterIsBound() throws Exception {
        List rows = new PolicyDAO().findByLine("AUTO' or '1'='1");
        assertEquals(0, rows.size());
    }

    @Test
    public void policySearchScreenDoesNotWidenTheFilter() throws Exception {
        Map parameters = new HashMap();
        parameters.put("lineOfBusiness", "AUTO' or '1'='1");
        HttpServletRequest request = request(parameters);

        new PolicySearchAction().execute(mapping("policySearch"), null,
                request, response());

        assertEquals(new Integer(0), request.getAttribute("searchCount"));
    }

    @Test
    public void workbenchStatusUpdateStaysOnOneClaim() throws Exception {
        String payload = "INVESTIGATING' where 1=1 --";
        Map parameters = new HashMap();
        parameters.put("claimId", "1");
        parameters.put("status", payload);

        new WorkbenchStatusAction().execute(mapping("workbenchView"), null,
                request(parameters), response());

        ClaimDAO claims = new ClaimDAO();
        assertEquals(payload, claims.findById(1).getStatus());
        assertEquals("APPROVED", claims.findById(3).getStatus());
    }

    @Test
    public void workbenchAssignmentStaysOnOneColumn() throws Exception {
        String payload = "adjuster2', status = 'CLOSED";
        Map parameters = new HashMap();
        parameters.put("claimId", "4");
        parameters.put("adjuster", payload);

        new WorkbenchAssignAction().execute(mapping("workbenchView"), null,
                request(parameters), response());

        Claim claim = new ClaimDAO().findById(4);
        assertEquals(payload, claim.getAssignedAdjuster());
        assertEquals("DENIED", claim.getStatus());
    }

    private ActionMapping mapping(String forward) {
        ActionMapping mapping = new ActionMapping();
        mapping.addForwardConfig(new ActionForward(forward, "/screen.jsp",
                false));
        return mapping;
    }

    /** Minimal request stub exposing parameters, attributes and a session. */
    private HttpServletRequest request(final Map parameters) {
        final Map attributes = new HashMap();
        final Map sessionAttributes = new HashMap();
        final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpSession.class },
                new MapBackedHandler(sessionAttributes, null, null));
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class },
                new MapBackedHandler(attributes, parameters, session));
    }

    private HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletResponse.class },
                new MapBackedHandler(new HashMap(), null, null));
    }

    private static class MapBackedHandler implements InvocationHandler {

        private final Map attributes;
        private final Map parameters;
        private final HttpSession session;

        MapBackedHandler(Map attributes, Map parameters, HttpSession session) {
            this.attributes = attributes;
            this.parameters = parameters;
            this.session = session;
        }

        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if ("getParameter".equals(name)) {
                return parameters == null ? null
                        : parameters.get(arguments[0]);
            }
            if ("setAttribute".equals(name)) {
                attributes.put(arguments[0], arguments[1]);
                return null;
            }
            if ("getAttribute".equals(name)) {
                return attributes.get(arguments[0]);
            }
            if ("getSession".equals(name)) {
                return session;
            }
            return null;
        }
    }
}
