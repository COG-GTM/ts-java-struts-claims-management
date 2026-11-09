package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.model.Claim;

/** Exercises the CLAIM gateway against a freshly seeded HSQLDB file. */
public class ClaimDAOTest {

    private final ClaimDAO dao = new ClaimDAO();

    @Before
    public void seed() throws Exception {
        TestDatabase.reset();
    }

    @AfterClass
    public static void release() throws Exception {
        TestDatabase.release();
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
        assertNull(dao.findById(99999));
    }

    @Test
    public void findAllReturnsSeededClaimsInKeyOrder() throws Exception {
        List claims = dao.findAll();
        assertEquals(120, claims.size());
        assertEquals(1, ((Claim) claims.get(0)).getClaimId());
        assertEquals(120, ((Claim) claims.get(119)).getClaimId());
        assertEquals(120, dao.count());
    }

    @Test
    public void findByStatusFiltersAndOrders() throws Exception {
        List open = dao.findByStatus("OPEN");
        assertEquals(24, open.size());
        int previous = 0;
        for (int i = 0; i < open.size(); i++) {
            Claim claim = (Claim) open.get(i);
            assertEquals("OPEN", claim.getStatus());
            assertTrue(claim.getClaimId() > previous);
            previous = claim.getClaimId();
        }
        assertTrue(dao.findByStatus("REOPENED").isEmpty());
    }

    @Test
    public void searchMatchesDescriptionFragment() throws Exception {
        List matches = dao.search("claim 11", null);
        assertEquals(11, matches.size());
        for (int i = 0; i < matches.size(); i++) {
            assertTrue(((Claim) matches.get(i)).getDescription()
                    .indexOf("claim 11") >= 0);
        }
    }

    @Test
    public void searchAppliesStatusWhenSupplied() throws Exception {
        List all = dao.search("Seeded", "");
        assertEquals(120, all.size());
        List closed = dao.search("Seeded", "CLOSED");
        assertEquals(24, closed.size());
        assertEquals("CLOSED", ((Claim) closed.get(0)).getStatus());
    }

    @Test
    public void assignStatusAndReserveUpdatesPersist() throws Exception {
        dao.assign(1, "adjuster3");
        dao.updateStatus(1, "APPROVED");
        dao.updateReserve(1, 7250.5);
        Claim claim = dao.findById(1);
        assertEquals("adjuster3", claim.getAssignedAdjuster());
        assertEquals("APPROVED", claim.getStatus());
        assertEquals(7250.5, claim.getReserveAmount(), 0.0);
        assertEquals(25, dao.findByStatus("APPROVED").size());
    }

    @Test
    public void updatesForUnknownClaimChangeNothing() throws Exception {
        dao.updateStatus(99999, "CLOSED");
        dao.updateReserve(99999, 1);
        assertEquals(24, dao.findByStatus("CLOSED").size());
        assertEquals(120, dao.count());
    }

    @Test
    public void deleteOfClaimWithChildRowsIsBlockedByForeignKeys() throws Exception {
        try {
            dao.delete(120);
            org.junit.Assert.fail("expected foreign key violation");
        } catch (java.sql.SQLException expected) {
            assertEquals("CLM-00120", dao.findById(120).getClaimNumber());
            assertEquals(120, dao.count());
        }
    }
}
