package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.util.DatabaseBootstrap;

/**
 * Covers the object-level and function-level authorization enforced by the
 * screen actions: claim 3 belongs to adjuster3 and carries settlement 3.
 */
public class ActionAuthorizationTest {

    private static final int OWNED_CLAIM = 3;
    private static final int UNSETTLED_CLAIM = 9990;
    private static final double SETTLEMENT_AMOUNT = 1003.0;

    private final ActionMapping mapping = new NamedForwardMapping();

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
        insertUnsettledClaim();
    }

    /**
     * Releases pooled connections so the next test class can recreate the
     * file database, which shuts the engine down.
     */
    @AfterClass
    public static void releasePool() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    private static void insertUnsettledClaim() throws Exception {
        java.sql.Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:hsqldb:file:"
                        + System.getProperty("claims.db.path"), "SA", "");
        try {
            int policyId = new ClaimDAO().findById(OWNED_CLAIM).getPolicyId();
            connection.createStatement().executeUpdate("insert into CLAIM"
                    + " values (" + UNSETTLED_CLAIM + ", 'CLM-09990', "
                    + policyId + ", 'Unsettled Claimant', '2019-01-15',"
                    + " '2019-01-16', 'FIRE', 'Claim without settlement',"
                    + " 'OPEN', 1000, 'adjuster3', 'supervisor',"
                    + " '2019-01-16')");
            connection.commit();
        } finally {
            connection.close();
        }
    }

    @Test
    public void foreignAdjusterCannotReadClaim() throws Exception {
        FakeRequest request = FakeRequest.create("adjuster2")
                .parameter("claimId", String.valueOf(OWNED_CLAIM));
        ActionForward forward = new WorkbenchViewAction().execute(mapping,
                null, request.request(), null);
        assertEquals("denied", forward.getName());
    }

    @Test
    public void assignedAdjusterReadsOwnClaim() throws Exception {
        FakeRequest request = FakeRequest.create("adjuster3")
                .parameter("claimId", String.valueOf(OWNED_CLAIM));
        ActionForward forward = new WorkbenchViewAction().execute(mapping,
                null, request.request(), null);
        assertEquals("workbenchView", forward.getName());
    }

    @Test
    public void supervisorReadsAnyClaim() throws Exception {
        FakeRequest request = FakeRequest.create("supervisor")
                .parameter("claimId", String.valueOf(OWNED_CLAIM));
        ActionForward forward = new WorkbenchViewAction().execute(mapping,
                null, request.request(), null);
        assertEquals("workbenchView", forward.getName());
    }

    @Test
    public void foreignAdjusterCannotChangeReserve() throws Exception {
        double before = reserveOf(OWNED_CLAIM);
        FakeRequest request = FakeRequest.create("adjuster2")
                .parameter("claimId", String.valueOf(OWNED_CLAIM))
                .parameter("reserveAmount", "999.0");
        ActionForward forward = new WorkbenchReserveAction().execute(mapping,
                null, request.request(), null);
        assertEquals("denied", forward.getName());
        assertEquals(before, reserveOf(OWNED_CLAIM), 0.0);
    }

    @Test
    public void foreignAdjusterCannotChangeStatus() throws Exception {
        String before = statusOf(OWNED_CLAIM);
        FakeRequest request = FakeRequest.create("adjuster2")
                .parameter("claimId", String.valueOf(OWNED_CLAIM))
                .parameter("status", "CLOSED");
        ActionForward forward = new WorkbenchStatusAction().execute(mapping,
                null, request.request(), null);
        assertEquals("denied", forward.getName());
        assertEquals(before, statusOf(OWNED_CLAIM));
    }

    @Test
    public void unauthenticatedRequestIsDenied() throws Exception {
        FakeRequest request = FakeRequest.create(null)
                .parameter("claimId", String.valueOf(OWNED_CLAIM));
        ActionForward forward = new WorkbenchViewAction().execute(mapping,
                null, request.request(), null);
        assertEquals("denied", forward.getName());
    }

    @Test
    public void paymentRejectsGetRequests() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        FakeRequest request = paymentRequest("supervisor", "250")
                .method("GET");
        assertEquals("denied", issuePayment(request));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void paymentRejectsMissingCsrfToken() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        FakeRequest request = FakeRequest.create("supervisor")
                .parameter("claimId", String.valueOf(OWNED_CLAIM))
                .parameter("payeeName", "Claimant 3")
                .parameter("paymentMethod", "CHECK")
                .parameter("amount", "250");
        assertEquals("denied", issuePayment(request));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void paymentRejectsUnauthorizedRole() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        assertEquals("denied",
                issuePayment(paymentRequest("adjuster3", "250")));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void paymentRejectsAmountAboveSettlement() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        assertEquals("error", issuePayment(paymentRequest("supervisor",
                String.valueOf(SETTLEMENT_AMOUNT + 1))));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void paymentRejectsNonPositiveAmount() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        assertEquals("error", issuePayment(paymentRequest("supervisor", "0")));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void paymentRejectsBlankPayee() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        FakeRequest request = FakeRequest.create("supervisor")
                .withCsrfToken()
                .parameter("claimId", String.valueOf(OWNED_CLAIM))
                .parameter("payeeName", "   ")
                .parameter("paymentMethod", "CHECK")
                .parameter("amount", "250");
        assertEquals("error", issuePayment(request));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void paymentRejectsClaimWithoutSettlement() throws Exception {
        int claimId = UNSETTLED_CLAIM;
        int before = paymentCount(claimId);
        FakeRequest request = FakeRequest.create("supervisor")
                .withCsrfToken()
                .parameter("claimId", String.valueOf(claimId))
                .parameter("payeeName", "Claimant")
                .parameter("paymentMethod", "CHECK")
                .parameter("amount", "100");
        assertEquals("error", issuePayment(request));
        assertEquals(before, paymentCount(claimId));
    }

    @Test
    public void authorizedSupervisorIssuesPayment() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        assertEquals("payment",
                issuePayment(paymentRequest("supervisor", "250")));
        assertEquals(before + 1, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void portfolioScreensAreSupervisorOnly() throws Exception {
        assertEquals("denied", new ReportIndexAction().execute(mapping, null,
                FakeRequest.create("adjuster3").request(), null).getName());
        assertFalse("denied".equals(new ReportIndexAction().execute(mapping,
                null, FakeRequest.create("supervisor").request(), null)
                .getName()));
    }

    @Test
    public void policyScreensFollowClaimAssignment() throws Exception {
        int policyId = new ClaimDAO().findById(OWNED_CLAIM).getPolicyId();
        FakeRequest foreign = FakeRequest.create("adjuster2")
                .parameter("policyId", String.valueOf(policyId));
        assertEquals("denied", new PolicyViewAction().execute(mapping, null,
                foreign.request(), null).getName());
        FakeRequest owner = FakeRequest.create("adjuster3")
                .parameter("policyId", String.valueOf(policyId));
        assertFalse("denied".equals(new PolicyViewAction().execute(mapping,
                null, owner.request(), null).getName()));
    }

    @Test
    public void paymentRejectsForeignSessionToken() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        FakeRequest request = paymentRequest("supervisor", "250")
                .parameter(ClaimsActionSupport.CSRF_PARAMETER, "guessed");
        assertEquals("denied", issuePayment(request));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    private FakeRequest paymentRequest(String operator, String amount) {
        return FakeRequest.create(operator)
                .withCsrfToken()
                .parameter("claimId", String.valueOf(OWNED_CLAIM))
                .parameter("payeeName", "Claimant 3")
                .parameter("paymentMethod", "CHECK")
                .parameter("amount", amount);
    }

    private String issuePayment(FakeRequest request) throws Exception {
        return new PaymentIssueAction().execute(mapping, null,
                request.request(), null).getName();
    }

    private int paymentCount(int claimId) throws Exception {
        return new PaymentDAO().findByClaim(claimId).size();
    }

    private double reserveOf(int claimId) throws Exception {
        return new ClaimDAO().findById(claimId).getReserveAmount();
    }

    private String statusOf(int claimId) throws Exception {
        return new ClaimDAO().findById(claimId).getStatus();
    }

}
