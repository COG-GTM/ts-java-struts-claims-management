package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;

/**
 * Proves that claim screens only serve the operator who owns the claim.
 *
 * Seeded claim 1 belongs to adjuster1, claim 2 to adjuster2, and the
 * supervisor account carries the region that covers every queue.
 */
public class ClaimAuthorizationTest {

    private static final int OWNED_BY_ADJUSTER1 = 1;

    private ActionMapping mapping;

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
        ConnectionPool.getInstance().closeAll();
    }

    @Before
    public void mapping() {
        mapping = new ActionMapping();
        mapping.addForwardConfig(
                new ActionForward("accessDenied", "/accessDenied", false));
        mapping.addForwardConfig(
                new ActionForward("workbenchView", "/workbenchView", false));
        mapping.addForwardConfig(
                new ActionForward("workbenchList", "/workbenchList", false));
        mapping.addForwardConfig(
                new ActionForward("payment", "/payment", false));
    }

    @Test
    public void refusesClaimReadFromAnotherAdjuster() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request("adjuster2")
                .parameter("claimId", String.valueOf(OWNED_BY_ADJUSTER1));
        ServletStubs.Response response = new ServletStubs.Response();

        ActionForward forward = new WorkbenchViewAction()
                .execute(mapping, null, request, response);

        assertEquals("accessDenied", forward.getName());
        assertNull(request.getAttribute("claim"));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status());
    }

    @Test
    public void refusesReassignmentOfAnotherAdjustersClaim() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request("adjuster2")
                .parameter("claimId", String.valueOf(OWNED_BY_ADJUSTER1))
                .parameter("adjuster", "adjuster2");

        ActionForward forward = new WorkbenchAssignAction().execute(
                mapping, null, request, new ServletStubs.Response());

        assertEquals("accessDenied", forward.getName());
        assertEquals("adjuster1", new ClaimDAO()
                .findById(OWNED_BY_ADJUSTER1).getAssignedAdjuster());
    }

    @Test
    public void refusesReserveChangeOnAnotherAdjustersClaim()
            throws Exception {
        Claim before = new ClaimDAO().findById(OWNED_BY_ADJUSTER1);
        ServletStubs.Request request = new ServletStubs.Request("adjuster3")
                .parameter("claimId", String.valueOf(OWNED_BY_ADJUSTER1))
                .parameter("reserveAmount", "999999");

        ActionForward forward = new WorkbenchReserveAction().execute(
                mapping, null, request, new ServletStubs.Response());

        assertEquals("accessDenied", forward.getName());
        assertEquals(before.getReserveAmount(), new ClaimDAO()
                .findById(OWNED_BY_ADJUSTER1).getReserveAmount(), 0.0);
    }

    @Test
    public void refusesPaymentIssuedAgainstAnotherAdjustersClaim()
            throws Exception {
        int before = new PaymentDAO().findByClaim(OWNED_BY_ADJUSTER1).size();
        ServletStubs.Request request = new ServletStubs.Request("adjuster4")
                .parameter("claimId", String.valueOf(OWNED_BY_ADJUSTER1))
                .parameter("payeeName", "Attacker")
                .parameter("amount", "5000");

        ActionForward forward = new PaymentIssueAction().execute(
                mapping, null, request, new ServletStubs.Response());

        assertEquals("accessDenied", forward.getName());
        assertEquals(before,
                new PaymentDAO().findByClaim(OWNED_BY_ADJUSTER1).size());
    }

    @Test
    public void refusesPaymentHistoryOfAnotherAdjustersClaim()
            throws Exception {
        ServletStubs.Request request = new ServletStubs.Request("adjuster5")
                .parameter("claimId", String.valueOf(OWNED_BY_ADJUSTER1));

        ActionForward forward = new PaymentHistoryAction().execute(
                mapping, null, request, new ServletStubs.Response());

        assertEquals("accessDenied", forward.getName());
        assertNull(request.getAttribute("payments"));
    }

    @Test
    public void servesClaimToItsOwnAdjuster() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request("adjuster1")
                .parameter("claimId", String.valueOf(OWNED_BY_ADJUSTER1));

        ActionForward forward = new WorkbenchViewAction().execute(
                mapping, null, request, new ServletStubs.Response());

        assertEquals("workbenchView", forward.getName());
        assertNotNull(request.getAttribute("claim"));
    }

    @Test
    public void servesAnyClaimToTheSupervisor() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request("supervisor")
                .parameter("claimId", String.valueOf(OWNED_BY_ADJUSTER1));

        ActionForward forward = new WorkbenchViewAction().execute(
                mapping, null, request, new ServletStubs.Response());

        assertEquals("workbenchView", forward.getName());
        assertNotNull(request.getAttribute("claim"));
    }

    @Test
    public void listsOnlyTheClaimsAssignedToTheOperator() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request("adjuster1");

        new WorkbenchListAction().execute(
                mapping, null, request, new ServletStubs.Response());

        List claims = (List) request.getAttribute("claims");
        assertTrue(claims.size() > 0);
        for (Iterator it = claims.iterator(); it.hasNext();) {
            assertEquals("adjuster1",
                    ((Claim) it.next()).getAssignedAdjuster());
        }
    }
}
