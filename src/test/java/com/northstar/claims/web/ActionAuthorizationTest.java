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
    private static final int CAPPED_CLAIM = 9991;
    private static final double CAPPED_SETTLEMENT = 100.0;
    private static final double SETTLEMENT_AMOUNT = 1003.0;

    private final ActionMapping mapping = new NamedForwardMapping();

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        ConnectionPool.getInstance().closeAll();
        DatabaseBootstrap.bootstrap(true);
        insertUnsettledClaim();
        insertCappedClaim();
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

    private static void insertCappedClaim() throws Exception {
        java.sql.Connection connection = java.sql.DriverManager.getConnection(
                "jdbc:hsqldb:file:"
                        + System.getProperty("claims.db.path"), "SA", "");
        try {
            int policyId = new ClaimDAO().findById(OWNED_CLAIM).getPolicyId();
            connection.createStatement().executeUpdate("insert into CLAIM"
                    + " values (" + CAPPED_CLAIM + ", 'CLM-09991', "
                    + policyId + ", 'Capped Claimant', '2019-01-15',"
                    + " '2019-01-16', 'FIRE', 'Claim with a small settlement',"
                    + " 'OPEN', 1000, 'adjuster3', 'supervisor',"
                    + " '2019-01-16')");
            connection.createStatement().executeUpdate("insert into"
                    + " SETTLEMENT values (9991, " + CAPPED_CLAIM + ", 100,"
                    + " 0, 0, FALSE, " + CAPPED_SETTLEMENT
                    + ", 'supervisor', '2019-03-01')");
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
    public void paymentRejectsMalformedAmount() throws Exception {
        int before = paymentCount(OWNED_CLAIM);
        assertEquals("error",
                issuePayment(paymentRequest("supervisor", "not-a-number")));
        assertEquals(before, paymentCount(OWNED_CLAIM));
    }

    @Test
    public void paymentRejectsAmountAboveRemainingBalance() throws Exception {
        FakeRequest first = cappedPaymentRequest(
                String.valueOf(CAPPED_SETTLEMENT));
        assertEquals("payment", issuePayment(first));
        int after = paymentCount(CAPPED_CLAIM);
        assertEquals("error", issuePayment(cappedPaymentRequest("1")));
        assertEquals(after, paymentCount(CAPPED_CLAIM));
    }

    /** The balance guard also holds when two requests race past the check. */
    @Test
    public void paymentInsertRefusesToOverspendSettlement() throws Exception {
        com.northstar.claims.model.Payment payment =
                new com.northstar.claims.model.Payment();
        payment.setPaymentId(99991);
        payment.setClaimId(CAPPED_CLAIM);
        payment.setSettlementId(9991);
        payment.setPayeeName("Racing Claimant");
        payment.setAmount(CAPPED_SETTLEMENT + 1);
        payment.setPaymentMethod("CHECK");
        payment.setCheckNumber("CHK-99991");
        payment.setIssuedDate("2019-04-03");
        payment.setStatus("ISSUED");
        int before = paymentCount(CAPPED_CLAIM);
        assertFalse(new com.northstar.claims.dao.PaymentDAO()
                .insertWithinSettlement(payment));
        assertEquals(before, paymentCount(CAPPED_CLAIM));
    }

    @Test
    public void forgedSupervisorRoleIsIgnored() throws Exception {
        FakeRequest request = FakeRequest.create("adjuster2")
                .sessionRole(ClaimsAuthorization.ROLE_SUPERVISOR)
                .parameter("claimId", String.valueOf(OWNED_CLAIM));
        assertEquals("denied", new WorkbenchViewAction().execute(mapping,
                null, request.request(), null).getName());
    }

    @Test
    public void claimListHidesForeignClaims() throws Exception {
        FakeRequest request = FakeRequest.create("adjuster3");
        new WorkbenchListAction().execute(mapping, null, request.request(),
                null);
        java.util.List claims = (java.util.List) request.attribute("claims");
        assertFalse(claims.isEmpty());
        for (int i = 0; i < claims.size(); i++) {
            assertEquals("adjuster3", ((com.northstar.claims.model.Claim)
                    claims.get(i)).getAssignedAdjuster());
        }
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

    private FakeRequest cappedPaymentRequest(String amount) {
        return FakeRequest.create("supervisor")
                .withCsrfToken()
                .parameter("claimId", String.valueOf(CAPPED_CLAIM))
                .parameter("payeeName", "Capped Claimant")
                .parameter("paymentMethod", "CHECK")
                .parameter("amount", amount);
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
