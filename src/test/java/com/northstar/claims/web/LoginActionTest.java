package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.AdjusterDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Adjuster;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies that login accepts only stored operator credentials. */
public class LoginActionTest {

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
        ConnectionPool.getInstance().closeAll();
    }

    @AfterClass
    public static void release() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    @Test
    public void rejectsAdjusterPrefixBackdoor() throws Exception {
        assertRejected("adjusterX", "legacyX");
        assertRejected("adjuster1", "legacy");
        assertRejected("adjuster1", "legacy9");
    }

    @Test
    public void rejectsGuessedSupervisorVariants() throws Exception {
        assertRejected("supervisor", "Supervisor");
        assertRejected("supervisor", "");
        assertRejected("supervisorX", "supervisor");
    }

    @Test
    public void acceptsSeededSupervisor() throws Exception {
        Map attributes = login("supervisor", "supervisor", "home");
        assertEquals("supervisor", attributes.get("user"));
        assertEquals("supervisor", attributes.get("displayName"));
        assertEquals("SUPERVISOR", attributes.get("role"));
    }

    @Test
    public void acceptsSeededAdjuster() throws Exception {
        Map attributes = login("adjuster1", "legacy1", "home");
        assertEquals("adjuster1", attributes.get("user"));
        assertEquals("ADJUSTER", attributes.get("role"));
    }

    @Test
    public void storesOnlyHashedCredentials() throws Exception {
        List adjusters = new AdjusterDAO().findAll();
        assertTrue(adjusters.size() > 0);
        for (Iterator i = adjusters.iterator(); i.hasNext();) {
            Adjuster adjuster = (Adjuster) i.next();
            assertTrue(adjuster.getUsername(), adjuster.getPasswordHash()
                    .startsWith("pbkdf2-sha1$"));
        }
    }

    private void assertRejected(String username, String password)
            throws Exception {
        Map attributes = login(username, password, "login");
        assertNull(attributes.get("user"));
        assertNull(attributes.get("role"));
    }

    private Map login(String username, String password, String expectedForward)
            throws Exception {
        Map parameters = new HashMap();
        parameters.put("username", username);
        parameters.put("password", password);
        Map attributes = new HashMap();
        HttpSession session = ServletStubs.session(attributes);
        HttpServletRequest request = ServletStubs.request(
                "/claims/login.do", parameters, session);
        ActionForward forward = new LoginAction().execute(mapping(), null,
                request, ServletStubs.response(new HashMap()));
        assertEquals(expectedForward, forward.getName());
        return attributes;
    }

    private ActionMapping mapping() {
        ActionMapping mapping = new ActionMapping();
        mapping.addForwardConfig(
                forward("home", "/WEB-INF/jsp/home.jsp"));
        mapping.addForwardConfig(
                forward("login", "/WEB-INF/jsp/login.jsp"));
        return mapping;
    }

    private ActionForward forward(String name, String path) {
        ActionForward value = new ActionForward();
        value.setName(name);
        value.setPath(path);
        return value;
    }
}
