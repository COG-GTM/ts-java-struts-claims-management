package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;

/** Exercises the CLAIM JDBC gateway against a freshly seeded HSQLDB file. */
public class ClaimDaoTest {

    private static final String DB_PATH = "target/db/test-northstar";

    private final ClaimDAO dao = new ClaimDAO();

    @Before
    public void reseed() throws Exception {
        System.setProperty("claims.db.path", DB_PATH);
        DatabaseBootstrap.bootstrap(true);
        // Bootstrap shuts the database down, so pooled connections are stale.
        ConnectionPool.getInstance().closeAll();
    }

    @AfterClass
    public static void releasePool() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    private static void execute(String sql) throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:hsqldb:file:" + DB_PATH, "SA", "");
        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate(sql);
            statement.close();
        } finally {
            connection.close();
        }
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
        assertNull(dao.findById(99999));
    }

    @Test
    public void findAllReturnsEverySeededClaimInIdOrder() throws Exception {
        List claims = dao.findAll();
        assertEquals(120, claims.size());
        assertEquals(dao.count(), claims.size());
        for (int i = 0; i < claims.size(); i++) {
            assertEquals(i + 1, ((Claim) claims.get(i)).getClaimId());
        }
    }

    @Test
    public void findByStatusFiltersAndOrdersByClaimId() throws Exception {
        List open = dao.findByStatus("OPEN");
        assertEquals(24, open.size());
        int previous = 0;
        for (int i = 0; i < open.size(); i++) {
            Claim claim = (Claim) open.get(i);
            assertEquals("OPEN", claim.getStatus());
            assertTrue(claim.getClaimId() > previous);
            previous = claim.getClaimId();
        }
        assertEquals(1, ((Claim) open.get(0)).getClaimId());
    }

    @Test
    public void findByStatusIsCaseSensitiveAndEmptyForUnknownStatus() throws Exception {
        assertTrue(dao.findByStatus("open").isEmpty());
        assertTrue(dao.findByStatus("REOPENED").isEmpty());
    }

    @Test
    public void searchMatchesDescriptionFragment() throws Exception {
        List claims = dao.search("claim 11", null);
        assertEquals(11, claims.size());
        for (int i = 0; i < claims.size(); i++) {
            assertTrue(((Claim) claims.get(i)).getDescription().indexOf("claim 11") >= 0);
        }
        assertEquals(11, dao.search("claim 11", "").size());
    }

    @Test
    public void searchAppliesOptionalStatusFilter() throws Exception {
        List claims = dao.search("claim 11", "DENIED");
        assertEquals(2, claims.size());
        assertEquals(114, ((Claim) claims.get(0)).getClaimId());
        assertEquals(119, ((Claim) claims.get(1)).getClaimId());
        assertTrue(dao.search("no such description", null).isEmpty());
    }

    @Test
    public void assignChangesAdjusterOnlyForTargetClaim() throws Exception {
        dao.assign(1, "adjuster5");
        assertEquals("adjuster5", dao.findById(1).getAssignedAdjuster());
        assertEquals("adjuster2", dao.findById(2).getAssignedAdjuster());
    }

    @Test
    public void updateStatusMovesClaimBetweenWorkQueues() throws Exception {
        dao.updateStatus(1, "CLOSED");
        assertEquals("CLOSED", dao.findById(1).getStatus());
        assertEquals(23, dao.findByStatus("OPEN").size());
    }

    @Test
    public void updateReservePersistsNewAmount() throws Exception {
        dao.updateReserve(3, 2500.75);
        assertEquals(2500.75, dao.findById(3).getReserveAmount(), 0.0);
        assertEquals(1001.0, dao.findById(1).getReserveAmount(), 0.0);
    }

    @Test
    public void updatesToUnknownClaimAreNoOps() throws Exception {
        dao.updateStatus(99999, "CLOSED");
        dao.assign(99999, "adjuster1");
        dao.updateReserve(99999, 1.0);
        assertEquals(120, dao.count());
        assertEquals(24, dao.findByStatus("OPEN").size());
    }

    @Test
    public void deleteRemovesClaimWithoutDependents() throws Exception {
        execute("INSERT INTO CLAIM VALUES (500, 'CLM-00500', 1, 'Temp', "
                + "'2019-01-15', '2019-03-15', 'FIRE', 'Temp claim', 'OPEN', 10, "
                + "'adjuster1', 'supervisor', '2019-03-15')");
        assertEquals(121, dao.count());
        dao.delete(500);
        assertNull(dao.findById(500));
        assertEquals(120, dao.count());
    }

    @Test(expected = SQLException.class)
    public void deleteRefusesClaimThatHasNotes() throws Exception {
        dao.delete(1);
    }
}
