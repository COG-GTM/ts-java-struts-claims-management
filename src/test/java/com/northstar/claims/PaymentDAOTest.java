package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.model.Payment;
import com.northstar.claims.util.DatabaseBootstrap;

/** Exercises the PAYMENT gateway against a freshly seeded local HSQLDB file. */
public class PaymentDAOTest {

    private final PaymentDAO dao = new PaymentDAO();

    @Before
    public void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        ConnectionPool.getInstance().closeAll();
        DatabaseBootstrap.bootstrap(true);
    }

    @AfterClass
    public static void releaseConnections() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    private Payment payment(int id, int claimId, double amount) {
        Payment value = new Payment();
        value.setPaymentId(id);
        value.setClaimId(claimId);
        value.setSettlementId(claimId);
        value.setPayeeName("Claimant " + claimId);
        value.setAmount(amount);
        value.setPaymentMethod("EFT");
        value.setCheckNumber("CHK-" + id);
        value.setIssuedDate("2019-04-01");
        value.setStatus("ISSUED");
        return value;
    }

    @Test
    public void findByIdMapsEveryColumn() throws Exception {
        Payment value = dao.findById(2);
        assertNotNull(value);
        assertEquals(2, value.getPaymentId());
        assertEquals(2, value.getClaimId());
        assertEquals(2, value.getSettlementId());
        assertEquals("Claimant 2", value.getPayeeName());
        assertEquals(250.0, value.getAmount(), 0.0);
        assertEquals("CHECK", value.getPaymentMethod());
        assertEquals("CHK-00002", value.getCheckNumber());
        assertEquals("2019-03-15", value.getIssuedDate());
        assertEquals("ISSUED", value.getStatus());
    }

    @Test
    public void findByIdReturnsNullForUnknownPayment() throws Exception {
        assertNull(dao.findById(987654));
    }

    @Test
    public void findAllAndCountAgreeWithSeed() throws Exception {
        List rows = dao.findAll();
        assertEquals(60, rows.size());
        assertEquals(60, dao.count());
        assertEquals(1, ((Payment) rows.get(0)).getPaymentId());
        assertEquals(60, ((Payment) rows.get(59)).getPaymentId());
    }

    @Test
    public void findByClaimReturnsOnlyThatClaimsPayments() throws Exception {
        List rows = dao.findByClaim(3);
        assertEquals(1, rows.size());
        assertEquals(3, ((Payment) rows.get(0)).getClaimId());
        assertTrue(dao.findByClaim(61).isEmpty());
    }

    @Test
    public void totalIssuedSumsPaymentsForClaim() throws Exception {
        assertEquals(500.0, dao.totalIssued(1), 0.0);
        assertEquals(250.0, dao.totalIssued(2), 0.0);
    }

    @Test
    public void totalIssuedIsZeroWhenClaimHasNoPayments() throws Exception {
        assertEquals(0.0, dao.totalIssued(61), 0.0);
    }

    @Test
    public void insertPersistsPaymentAndUpdatesTotals() throws Exception {
        dao.insert(payment(500, 1, 125.25));
        Payment stored = dao.findById(500);
        assertNotNull(stored);
        assertEquals(125.25, stored.getAmount(), 0.0);
        assertEquals("EFT", stored.getPaymentMethod());
        assertEquals("2019-04-01", stored.getIssuedDate());
        assertEquals(625.25, dao.totalIssued(1), 0.0);
        assertEquals(2, dao.findByClaim(1).size());
        assertEquals(61, dao.count());
    }

    @Test(expected = java.sql.SQLException.class)
    public void insertRejectsDuplicatePaymentId() throws Exception {
        dao.insert(payment(1, 1, 10));
    }

    @Test(expected = java.sql.SQLException.class)
    public void insertRejectsUnknownClaim() throws Exception {
        dao.insert(payment(501, 987654, 10));
    }

    @Test
    public void deleteRemovesPayment() throws Exception {
        dao.delete(1);
        assertNull(dao.findById(1));
        assertEquals(0.0, dao.totalIssued(1), 0.0);
        assertEquals(59, dao.count());
    }
}
