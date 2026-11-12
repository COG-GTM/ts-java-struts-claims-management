package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies the workbench update actions bind and validate request values. */
public class WorkbenchUpdateActionTest {

    private static final int CLAIM_ID = 119;

    @Before
    public void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
        ConnectionPool.getInstance().closeAll();
    }

    @After
    public void releaseConnections() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    @Test
    public void assignsActiveAdjuster() throws Exception {
        RecordingResponse response = new RecordingResponse();
        ActionForward forward = new WorkbenchAssignAction().execute(mapping(),
                null, request("adjuster", "adjuster3"), response.proxy());
        assertNotNull(forward);
        assertNull(response.error);
        assertEquals("adjuster3", claim().getAssignedAdjuster());
    }

    @Test
    public void rejectsInjectedAdjuster() throws Exception {
        Claim before = claim();
        RecordingResponse response = new RecordingResponse();
        ActionForward forward = new WorkbenchAssignAction().execute(mapping(),
                null, request("adjuster", "adjuster3', status='CLOSED"),
                response.proxy());
        assertNull(forward);
        assertEquals(new Integer(HttpServletResponse.SC_BAD_REQUEST),
                response.error);
        Claim after = claim();
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getAssignedAdjuster(), after.getAssignedAdjuster());
    }

    @Test
    public void rejectsUnknownAdjuster() throws Exception {
        Claim before = claim();
        RecordingResponse response = new RecordingResponse();
        new WorkbenchAssignAction().execute(mapping(), null,
                request("adjuster", "nobody"), response.proxy());
        assertEquals(new Integer(HttpServletResponse.SC_BAD_REQUEST),
                response.error);
        assertEquals(before.getAssignedAdjuster(),
                claim().getAssignedAdjuster());
    }

    @Test
    public void updatesKnownStatus() throws Exception {
        RecordingResponse response = new RecordingResponse();
        ActionForward forward = new WorkbenchStatusAction().execute(mapping(),
                null, request("status", "investigating"), response.proxy());
        assertNotNull(forward);
        assertNull(response.error);
        assertEquals("INVESTIGATING", claim().getStatus());
    }

    @Test
    public void rejectsInjectedStatus() throws Exception {
        Claim before = claim();
        RecordingResponse response = new RecordingResponse();
        ActionForward forward = new WorkbenchStatusAction().execute(mapping(),
                null, request("status", "CLOSED', assigned_adjuster='x"),
                response.proxy());
        assertNull(forward);
        assertEquals(new Integer(HttpServletResponse.SC_BAD_REQUEST),
                response.error);
        Claim after = claim();
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getAssignedAdjuster(), after.getAssignedAdjuster());
    }

    private Claim claim() throws Exception {
        return new ClaimDAO().findById(CLAIM_ID);
    }

    private ActionMapping mapping() {
        ActionMapping mapping = new ActionMapping();
        mapping.addForwardConfig(new ActionForward("workbenchView",
                "/WEB-INF/jsp/workbench/view.jsp", false));
        return mapping;
    }

    private HttpServletRequest request(String name, String value) {
        final Map parameters = new HashMap();
        parameters.put("claimId", String.valueOf(CLAIM_ID));
        parameters.put(name, value);
        final Map attributes = new HashMap();
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method,
                            Object[] args) {
                        String call = method.getName();
                        if ("getParameter".equals(call)) {
                            return parameters.get(args[0]);
                        }
                        if ("setAttribute".equals(call)) {
                            attributes.put(args[0], args[1]);
                            return null;
                        }
                        if ("getAttribute".equals(call)) {
                            return attributes.get(args[0]);
                        }
                        throw new UnsupportedOperationException(call);
                    }
                });
    }

    private static class RecordingResponse implements InvocationHandler {
        Integer error;

        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { HttpServletResponse.class }, this);
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("sendError".equals(method.getName())) {
                error = (Integer) args[0];
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
