package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;

/** Verifies which credentials the login action accepts and the session it builds. */
public class LoginActionTest {

    private final ActionMapping mapping = ServletStubs.mapping(new String[] { "home", "login" });

    private ActionForward login(ServletStubs.Request request) throws Exception {
        return new LoginAction().execute(mapping, null, request.asRequest(),
                new ServletStubs.Response().asResponse());
    }

    @Test
    public void supervisorCredentialsAuthenticate() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request()
                .param("username", "supervisor").param("password", "supervisor");
        ActionForward forward = login(request);
        assertEquals("home", forward.getName());
        assertEquals("supervisor", request.sessionAttributes.get("user"));
        assertEquals("supervisor", request.sessionAttributes.get("displayName"));
        assertEquals("AUTHENTICATED", request.attributes.get("loginStatus"));
        assertNull(request.attributes.get("message"));
    }

    @Test
    public void seededAdjusterWithLegacyPasswordAuthenticates() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request()
                .param("username", "adjuster2").param("password", "legacy2");
        assertEquals("home", login(request).getName());
        assertEquals("adjuster2", request.sessionAttributes.get("user"));
    }

    private void assertRejected(ServletStubs.Request request) throws Exception {
        ActionForward forward = login(request);
        assertEquals("login", forward.getName());
        assertEquals("login.failed", request.attributes.get("message"));
        assertEquals("REJECTED", request.attributes.get("loginStatus"));
        assertNull(request.sessionAttributes.get("user"));
    }

    @Test
    public void supervisorWithWrongPasswordIsRejected() throws Exception {
        assertRejected(new ServletStubs.Request()
                .param("username", "supervisor").param("password", "legacy"));
    }

    @Test
    public void adjusterWithoutLegacyPasswordIsRejected() throws Exception {
        assertRejected(new ServletStubs.Request()
                .param("username", "adjuster1").param("password", "supervisor"));
    }

    @Test
    public void unknownUserPrefixIsRejected() throws Exception {
        assertRejected(new ServletStubs.Request()
                .param("username", "auditor").param("password", "legacy"));
    }

    @Test
    public void missingUsernameIsRejected() throws Exception {
        assertRejected(new ServletStubs.Request().param("password", "supervisor"));
    }

    @Test
    public void missingPasswordIsRejected() throws Exception {
        assertRejected(new ServletStubs.Request().param("username", "supervisor"));
    }

    @Test
    public void usernameMatchIsCaseSensitive() throws Exception {
        assertRejected(new ServletStubs.Request()
                .param("username", "Supervisor").param("password", "supervisor"));
    }
}
