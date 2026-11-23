package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.dao.SettlementDAO;
import com.northstar.claims.model.Settlement;
import com.northstar.claims.service.SettlementService;
import com.northstar.claims.util.DatabaseBootstrap;

/** Exercises settlement persistence and the calculate-and-save service. */
public class SettlementDAOTest {

    private final SettlementDAO dao = new SettlementDAO();

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

    @Test
    public void findByIdMapsEveryColumn() throws Exception {
        Settlement value = dao.findById(3);
        assertNotNull(value);
        assertEquals(3, value.getSettlementId());
        assertEquals(3, value.getClaimId());
        assertEquals(1003.0, value.getCoveredAmount(), 0.0);
        assertEquals(500.0, value.getDeductibleApplied(), 0.0);
        assertEquals(100.0, value.getDepreciation(), 0.0);
        assertFalse(value.isCappedAtLimit());
        assertEquals(1003.0, value.getSettlementAmount(), 0.0);
        assertEquals("adjuster1", value.getCalculatedBy());
        assertEquals("2019-03-01", value.getCalculatedDate());
    }

    @Test
    public void findByIdReturnsNullForUnknownSettlement() throws Exception {
        assertNull(dao.findById(987654));
    }

    @Test
    public void findAllAndCountAgreeWithSeed() throws Exception {
        List rows = dao.findAll();
        assertEquals(120, rows.size());
        assertEquals(120, dao.count());
        assertEquals(1, ((Settlement) rows.get(0)).getSettlementId());
    }

    @Test
    public void findByClaimReturnsNullWhenClaimHasNoSettlement() throws Exception {
        assertNull(dao.findByClaim(987654));
    }

    @Test
    public void findByClaimReturnsLatestSettlement() throws Exception {
        Settlement newer = new Settlement();
        newer.setSettlementId(9999);
        newer.setClaimId(5);
        newer.setCoveredAmount(2000);
        newer.setDeductibleApplied(250);
        newer.setDepreciation(50);
        newer.setCappedAtLimit(true);
        newer.setSettlementAmount(1700);
        newer.setCalculatedBy("supervisor");
        newer.setCalculatedDate("2019-04-01");
        dao.save(newer);

        Settlement latest = dao.findByClaim(5);
        assertEquals(9999, latest.getSettlementId());
        assertTrue(latest.isCappedAtLimit());
        assertEquals(1700.0, latest.getSettlementAmount(), 0.0);
        assertEquals("2019-04-01", latest.getCalculatedDate());
        assertEquals(121, dao.count());
    }

    @Test(expected = java.sql.SQLException.class)
    public void saveRejectsDuplicateSettlementId() throws Exception {
        Settlement duplicate = dao.findById(1);
        dao.save(duplicate);
    }

    @Test
    public void deleteRemovesSettlementWithoutPayments() throws Exception {
        dao.delete(100);
        assertNull(dao.findById(100));
        assertEquals(119, dao.count());
    }

    @Test(expected = java.sql.SQLException.class)
    public void deleteRejectsSettlementReferencedByPayment() throws Exception {
        dao.delete(1);
    }

    @Test
    public void serviceCalculatesAndPersistsSettlement() throws Exception {
        SettlementService service = new SettlementService();
        Settlement saved = service.calculateAndSave(7, 5000, "250", 100, 1000);
        assertEquals(7, saved.getClaimId());
        assertEquals(10007, saved.getSettlementId());
        assertEquals(1000.0, saved.getSettlementAmount(), 0.0);
        assertTrue(saved.isCappedAtLimit());
        assertEquals("supervisor", saved.getCalculatedBy());

        Settlement stored = service.findByClaim(7);
        assertEquals(10007, stored.getSettlementId());
        assertEquals(5000.0, stored.getCoveredAmount(), 0.0);
        assertEquals(250.0, stored.getDeductibleApplied(), 0.0);
        assertEquals(1000.0, stored.getSettlementAmount(), 0.0);
        assertTrue(stored.isCappedAtLimit());
        assertEquals("2019-03-01", stored.getCalculatedDate());
    }

    @Test
    public void serviceSaveDelegatesToDao() throws Exception {
        SettlementService service = new SettlementService();
        Settlement value = dao.findById(8);
        value.setSettlementId(8888);
        service.save(value);
        assertEquals(8888, service.findByClaim(8).getSettlementId());
    }
}
