package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;

/** Exercises the CLAIM gateway against a freshly seeded local HSQLDB file. */
public class ClaimDaoIntegrationTest {

    private final ClaimDAO dao = new ClaimDAO();

    @BeforeClass
    public static void seed() throws Exception {
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
        Claim claim = dao.findById(2);
        assertEquals(2, claim.getClaimId());
        assertEquals("CLM-00002", claim.getClaimNumber());
        assertEquals(2, claim.getPolicyId());
        assertEquals("Claimant 2", claim.getClaimantName());
        assertEquals("2019-01-15", claim.getLossDate());
        assertEquals("2019-02-15", claim.getReportedDate());
        assertEquals("WATER", claim.getLossType());
        assertEquals("Seeded claim 2", claim.getDescription());
        assertEquals("INVESTIGATING", claim.getStatus());
        assertEquals(1002.0, claim.getReserveAmount(), 0.0);
        assertEquals("adjuster2", claim.getAssignedAdjuster());
        assertEquals("supervisor", claim.getCreatedBy());
        assertEquals("2019-02-15", claim.getCreatedDate());
    }

    @Test
    public void findByIdReturnsNullForUnknownClaim() throws Exception {
        assertNull(dao.findById(987654));
    }

    @Test
    public void findAllIsOrderedByPrimaryKey() throws Exception {
        List claims = dao.findAll();
        assertTrue(claims.size() >= 120);
        assertEquals(1, ((Claim) claims.get(0)).getClaimId());
        int previous = 0;
        for (int i = 0; i < claims.size(); i++) {
            int id = ((Claim) claims.get(i)).getClaimId();
            assertTrue(id > previous);
            previous = id;
        }
    }

    @Test
    public void findByStatusFiltersAndOrders() throws Exception {
        List denied = dao.findByStatus("DENIED");
        assertEquals(24, denied.size());
        for (int i = 0; i < denied.size(); i++) {
            assertEquals("DENIED", ((Claim) denied.get(i)).getStatus());
        }
        assertEquals(4, ((Claim) denied.get(0)).getClaimId());
        assertTrue(dao.findByStatus("NO_SUCH_STATUS").isEmpty());
    }

    @Test
    public void searchMatchesDescriptionWithOptionalStatus() throws Exception {
        List all = dao.search("Seeded claim 11", null);
        assertEquals(11, all.size());
        List blankStatus = dao.search("Seeded claim 11", "");
        assertEquals(11, blankStatus.size());
        List denied = dao.search("Seeded claim 11", "DENIED");
        assertEquals(2, denied.size());
        assertEquals(114, ((Claim) denied.get(0)).getClaimId());
        assertEquals(119, ((Claim) denied.get(1)).getClaimId());
        assertTrue(dao.search("no such description", null).isEmpty());
    }

    @Test
    public void assignStatusAndReserveUpdatesArePersisted() throws Exception {
        dao.assign(10, "adjuster3");
        dao.updateStatus(10, "APPROVED");
        dao.updateReserve(10, 4321.5);
        Claim claim = dao.findById(10);
        assertEquals("adjuster3", claim.getAssignedAdjuster());
        assertEquals("APPROVED", claim.getStatus());
        assertEquals(4321.5, claim.getReserveAmount(), 0.0);
    }

    @Test
    public void updatesForUnknownClaimAreNoOps() throws Exception {
        int before = dao.count();
        dao.updateStatus(987654, "CLOSED");
        dao.updateReserve(987654, 1);
        assertEquals(before, dao.count());
        assertNull(dao.findById(987654));
    }

    @Test
    public void deleteOfClaimWithNotesIsBlockedByForeignKey() throws Exception {
        int before = dao.count();
        try {
            dao.delete(120);
            org.junit.Assert.fail("expected referential integrity violation");
        } catch (java.sql.SQLException expected) {
            assertNotNull(dao.findById(120));
        }
        assertEquals(before, dao.count());
    }

    @Test
    public void deleteOfUnknownClaimLeavesCountUnchanged() throws Exception {
        int before = dao.count();
        dao.delete(987654);
        assertEquals(before, dao.count());
    }
}
