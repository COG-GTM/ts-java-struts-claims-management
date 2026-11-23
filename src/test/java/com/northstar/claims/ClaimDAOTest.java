package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;

/** Exercises the CLAIM gateway against a freshly seeded local HSQLDB file. */
public class ClaimDAOTest {

    private final ClaimDAO dao = new ClaimDAO();

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
        Claim claim = dao.findById(2);
        assertNotNull(claim);
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
    public void findAllReturnsSeededRowsInKeyOrder() throws Exception {
        List claims = dao.findAll();
        assertEquals(120, claims.size());
        assertEquals(1, ((Claim) claims.get(0)).getClaimId());
        assertEquals(120, ((Claim) claims.get(119)).getClaimId());
        for (int i = 1; i < claims.size(); i++) {
            assertTrue(((Claim) claims.get(i - 1)).getClaimId()
                    < ((Claim) claims.get(i)).getClaimId());
        }
    }

    @Test
    public void countMatchesSeed() throws Exception {
        assertEquals(120, dao.count());
    }

    @Test
    public void findByStatusFiltersAndOrders() throws Exception {
        List open = dao.findByStatus("OPEN");
        assertEquals(24, open.size());
        for (int i = 0; i < open.size(); i++) {
            assertEquals("OPEN", ((Claim) open.get(i)).getStatus());
        }
        assertEquals(1, ((Claim) open.get(0)).getClaimId());
    }

    @Test
    public void findByStatusReturnsEmptyListForUnknownStatus() throws Exception {
        assertTrue(dao.findByStatus("NO_SUCH_STATUS").isEmpty());
    }

    @Test
    public void searchMatchesDescriptionFragment() throws Exception {
        List claims = dao.search("claim 11", null);
        assertEquals(11, claims.size());
        for (int i = 0; i < claims.size(); i++) {
            assertTrue(((Claim) claims.get(i)).getDescription().indexOf("claim 11") >= 0);
        }
    }

    @Test
    public void searchAppliesStatusFilterWhenSupplied() throws Exception {
        List claims = dao.search("claim 11", "OPEN");
        assertTrue(claims.size() > 0);
        assertTrue(claims.size() < 11);
        for (int i = 0; i < claims.size(); i++) {
            assertEquals("OPEN", ((Claim) claims.get(i)).getStatus());
        }
    }

    @Test
    public void searchTreatsEmptyStatusAsNoFilter() throws Exception {
        assertEquals(dao.search("claim 11", null).size(),
                dao.search("claim 11", "").size());
    }

    @Test
    public void assignChangesAdjuster() throws Exception {
        dao.assign(1, "adjuster5");
        assertEquals("adjuster5", dao.findById(1).getAssignedAdjuster());
    }

    @Test
    public void updateStatusChangesWorkflowStatus() throws Exception {
        dao.updateStatus(1, "CLOSED");
        assertEquals("CLOSED", dao.findById(1).getStatus());
        assertEquals(23, dao.findByStatus("OPEN").size());
    }

    @Test
    public void updateReserveChangesAmountOnlyForTargetClaim() throws Exception {
        dao.updateReserve(1, 4321.5);
        assertEquals(4321.5, dao.findById(1).getReserveAmount(), 0.0);
        assertEquals(1002.0, dao.findById(2).getReserveAmount(), 0.0);
    }

    @Test
    public void deleteOfUnknownClaimIsNoOp() throws Exception {
        dao.delete(987654);
        assertEquals(120, dao.count());
    }

    @Test(expected = java.sql.SQLException.class)
    public void deleteRejectsClaimWithNotes() throws Exception {
        dao.delete(1);
    }

    @Test
    public void connectionsAreReturnedToPool() throws Exception {
        ConnectionPool.getInstance().closeAll();
        dao.count();
        dao.findById(1);
        assertEquals(1, ConnectionPool.getInstance().size());
    }
}
