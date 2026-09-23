package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.model.Policy;
import com.northstar.claims.util.DatabaseBootstrap;
import com.northstar.claims.web.PolicySearchAction;

/** Confirms the policy search screen cannot be used to inject SQL. */
public class PolicySearchInjectionTest {

    private static final String UNION_PAYLOAD =
            "AUTO' union select adjuster_id, username, password, full_name, region,"
            + " '2018-01-01', '2019-12-31', 0, 0, 0, 'ACTIVE' from ADJUSTER --";

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
    }

    @Test
    public void treatsInjectionPayloadAsALiteralFilter() throws Exception {
        List rows = new PolicyDAO().findByLine(UNION_PAYLOAD);
        assertEquals(0, rows.size());
    }

    @Test
    public void stillReturnsRowsForARealLineOfBusiness() throws Exception {
        List rows = new PolicyDAO().findByLine("AUTO");
        assertTrue(rows.size() > 0);
        for (Iterator i = rows.iterator(); i.hasNext();) {
            assertEquals("AUTO", ((Policy) i.next()).getLineOfBusiness());
        }
    }

    @Test
    public void actionRejectsMalformedLineOfBusiness() throws Exception {
        Map attributes = execute(UNION_PAYLOAD);
        assertEquals(0, ((List) attributes.get("policies")).size());
        assertEquals(new Integer(0), attributes.get("searchCount"));
    }

    @Test
    public void actionStillSearchesValidLineOfBusiness() throws Exception {
        Map attributes = execute("HOMEOWNERS");
        assertTrue(((List) attributes.get("policies")).size() > 0);
    }

    private Map execute(String lineOfBusiness) throws Exception {
        Map attributes = new HashMap();
        HttpServletRequest request = stubRequest(lineOfBusiness, attributes);
        ActionMapping mapping = new ActionMapping() {
            public ActionForward findForward(String name) {
                return new ActionForward(name, "/WEB-INF/jsp/policy/search.jsp", false);
            }
        };
        new PolicySearchAction().execute(mapping, null, request, null);
        return attributes;
    }

    private HttpServletRequest stubRequest(final String lineOfBusiness, final Map attributes) {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("getParameter".equals(name)) {
                    return "lineOfBusiness".equals(args[0]) ? lineOfBusiness : null;
                }
                if ("setAttribute".equals(name)) {
                    attributes.put(args[0], args[1]);
                    return null;
                }
                if ("getAttribute".equals(name)) {
                    return attributes.get(args[0]);
                }
                return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class },
                handler);
    }
}
