package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.model.Claim;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Exercises the claim JDBC gateway against the deterministic seed rows. */
public class ClaimDaoTest {

    private final ClaimDAO dao = new ClaimDAO();

    @BeforeClass
    public static void seedDatabase() throws Exception {
        TestDatabase.reset();
    }

    @AfterClass
    public static void releaseDatabase() throws Exception {
        TestDatabase.release();
    }

    @Test
    public void mapsEveryColumnOfASeededClaim() throws Exception {
        Claim claim = dao.findById(1);
        assertEquals(1, claim.getClaimId());
        assertEquals("CLM-00001", claim.getClaimNumber());
        assertEquals(1, claim.getPolicyId());
        assertEquals("Claimant 1", claim.getClaimantName());
        assertEquals("2019-01-15", claim.getLossDate());
        assertEquals("2019-03-15", claim.getReportedDate());
        assertEquals("FIRE", claim.getLossType());
        assertEquals("Seeded claim 1", claim.getDescription());
        assertEquals("OPEN", claim.getStatus());
        assertEquals(1001.0, claim.getReserveAmount(), 0.001);
        assertEquals("adjuster1", claim.getAssignedAdjuster());
        assertEquals("supervisor", claim.getCreatedBy());
        assertEquals("2019-03-15", claim.getCreatedDate());
    }

    @Test
    public void returnsNullForUnknownClaim() throws Exception {
        assertNull(dao.findById(99999));
    }

    @Test
    public void listsAllClaimsInIdOrder() throws Exception {
        List claims = dao.findAll();
        assertEquals(120, claims.size());
        assertEquals(dao.count(), claims.size());
        for (int i = 0; i < claims.size(); i++) {
            assertEquals(i + 1, ((Claim) claims.get(i)).getClaimId());
        }
    }

    @Test
    public void filtersByStatusInIdOrder() throws Exception {
        List open = dao.findByStatus("APPROVED");
        assertEquals(24, open.size());
        int previous = 0;
        for (int i = 0; i < open.size(); i++) {
            Claim claim = (Claim) open.get(i);
            assertEquals("APPROVED", claim.getStatus());
            assertTrue(claim.getClaimId() > previous);
            previous = claim.getClaimId();
        }
    }

    @Test
    public void unknownStatusReturnsEmptyList() throws Exception {
        assertTrue(dao.findByStatus("NOT_A_STATUS").isEmpty());
    }

    @Test
    public void searchMatchesDescriptionFragment() throws Exception {
        List matches = dao.search("claim 11", null);
        assertEquals(11, matches.size());
        for (int i = 0; i < matches.size(); i++) {
            assertTrue(((Claim) matches.get(i)).getDescription().startsWith("Seeded claim 11"));
        }
    }

    @Test
    public void searchTreatsBlankStatusAsNoFilter() throws Exception {
        assertEquals(dao.search("claim 11", null).size(), dao.search("claim 11", "").size());
    }

    @Test
    public void searchAppliesStatusFilter() throws Exception {
        List all = dao.search("claim 11", null);
        List denied = dao.search("claim 11", "DENIED");
        int expected = 0;
        for (int i = 0; i < all.size(); i++) {
            if ("DENIED".equals(((Claim) all.get(i)).getStatus())) {
                expected++;
            }
        }
        assertTrue(expected > 0);
        assertEquals(expected, denied.size());
        for (int i = 0; i < denied.size(); i++) {
            assertEquals("DENIED", ((Claim) denied.get(i)).getStatus());
        }
    }

    @Test
    public void searchWithoutMatchesReturnsEmptyList() throws Exception {
        assertTrue(dao.search("no such description", null).isEmpty());
    }

    @Test
    public void assignUpdatesOnlyTheTargetClaim() throws Exception {
        String original = dao.findById(5).getAssignedAdjuster();
        String neighbour = dao.findById(6).getAssignedAdjuster();
        try {
            dao.assign(5, "adjuster9");
            assertEquals("adjuster9", dao.findById(5).getAssignedAdjuster());
            assertEquals(neighbour, dao.findById(6).getAssignedAdjuster());
        } finally {
            dao.assign(5, original);
        }
    }

    @Test
    public void updateStatusPersistsNewStatus() throws Exception {
        String original = dao.findById(7).getStatus();
        try {
            dao.updateStatus(7, "CLOSED");
            assertEquals("CLOSED", dao.findById(7).getStatus());
        } finally {
            dao.updateStatus(7, original);
        }
        assertEquals(original, dao.findById(7).getStatus());
    }

    @Test
    public void updateReservePersistsAmount() throws Exception {
        double original = dao.findById(8).getReserveAmount();
        try {
            dao.updateReserve(8, 4321.25);
            assertEquals(4321.25, dao.findById(8).getReserveAmount(), 0.001);
        } finally {
            dao.updateReserve(8, original);
        }
    }

    @Test
    public void deleteRemovesClaimRow() throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:hsqldb:file:" + TestDatabase.PATH, "SA", "");
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate("insert into CLAIM values (5001, 'CLM-05001', 1, 'Temp',"
                    + " '2019-01-01', '2019-01-02', 'FIRE', 'Temporary claim', 'OPEN', 10,"
                    + " 'adjuster1', 'supervisor', '2019-01-02')");
        } finally {
            statement.close();
            connection.close();
        }
        int before = dao.count();
        assertEquals("Temp", dao.findById(5001).getClaimantName());
        dao.delete(5001);
        assertNull(dao.findById(5001));
        assertEquals(before - 1, dao.count());
    }

    @Test
    public void deletingUnknownClaimLeavesCountUnchanged() throws Exception {
        int before = dao.count();
        dao.delete(99999);
        assertEquals(before, dao.count());
    }
}
