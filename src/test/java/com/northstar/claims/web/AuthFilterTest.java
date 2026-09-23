package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.junit.Test;

/** Verifies session and role gating applied to protected claim screens. */
public class AuthFilterTest {

    @Test
    public void redirectsAnonymousRequests() throws Exception {
        Map record = filter("/claims/workbench/list.do", new HashMap());
        assertNull(record.get("chained"));
        assertEquals("/claims/login.do", record.get("redirect"));
    }

    @Test
    public void allowsPublicLoginScreen() throws Exception {
        Map record = filter("/claims/login.do", new HashMap());
        assertTrue(((Boolean) record.get("chained")).booleanValue());
    }

    @Test
    public void allowsAuthenticatedClaimScreens() throws Exception {
        Map record = filter("/claims/workbench/list.do",
                session("adjuster1", "ADJUSTER"));
        assertTrue(((Boolean) record.get("chained")).booleanValue());
    }

    @Test
    public void rejectsAdminModuleWithoutSupervisorRole() throws Exception {
        Map record = filter("/claims/admin/adjusters.do",
                session("adjuster1", "ADJUSTER"));
        assertNull(record.get("chained"));
        assertEquals(new Integer(403), record.get("error"));
    }

    @Test
    public void allowsAdminModuleForSupervisors() throws Exception {
        Map record = filter("/claims/admin/adjusters.do",
                session("supervisor", "SUPERVISOR"));
        assertTrue(((Boolean) record.get("chained")).booleanValue());
    }

    private Map session(String user, String role) {
        Map attributes = new HashMap();
        attributes.put("user", user);
        attributes.put("role", role);
        return attributes;
    }

    private Map filter(String uri, Map sessionAttributes) throws Exception {
        HttpSession session = ServletStubs.session(sessionAttributes);
        HttpServletRequest request = ServletStubs.request(uri, new HashMap(),
                session);
        Map record = new HashMap();
        new AuthFilter().doFilter(request, ServletStubs.response(record),
                ServletStubs.chain(record));
        return record;
    }
}
