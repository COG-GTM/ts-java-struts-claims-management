package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.dao.ReportDAO;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies the aggregates shown on the management report screens. */
public class ReportDAOTest {

    private final ReportDAO dao = new ReportDAO();

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

    private static double number(Map row, String key) {
        return ((Double) row.get(key)).doubleValue();
    }

    @Test
    public void openClaimsByAdjusterGroupsOpenAndInvestigatingClaims() throws Exception {
        List rows = dao.openClaimsByAdjuster();
        assertEquals(2, rows.size());
        Map first = (Map) rows.get(0);
        Map second = (Map) rows.get(1);
        assertEquals("adjuster1", first.get("adjuster"));
        assertEquals(24.0, number(first, "openCount"), 0.0);
        assertEquals(25404.0, number(first, "reserveTotal"), 0.001);
        assertEquals("adjuster2", second.get("adjuster"));
        assertEquals(24.0, number(second, "openCount"), 0.0);
        assertEquals(25428.0, number(second, "reserveTotal"), 0.001);
    }

    @Test
    public void openClaimsByAdjusterReflectsStatusChanges() throws Exception {
        new ClaimDAO().updateStatus(1, "CLOSED");
        Map first = (Map) dao.openClaimsByAdjuster().get(0);
        assertEquals(23.0, number(first, "openCount"), 0.0);
        assertEquals(25404.0 - 1001.0, number(first, "reserveTotal"), 0.001);
    }

    @Test
    public void agedClaimsBucketsActiveClaimsAgainstConfiguredDate() throws Exception {
        List rows = dao.agedClaims();
        assertEquals(4, rows.size());
        String[] buckets = { "0_30", "31_60", "61_90", "91_PLUS" };
        double[] reserves = { 19086.0, 19056.0, 19026.0, 19116.0 };
        for (int i = 0; i < buckets.length; i++) {
            Map row = (Map) rows.get(i);
            assertEquals(buckets[i], row.get("bucket"));
            assertEquals(18.0, number(row, "claimCount"), 0.0);
            assertEquals(reserves[i], number(row, "reserveTotal"), 0.001);
        }
    }

    @Test
    public void agedClaimsExcludesClosedAndDeniedClaims() throws Exception {
        new ClaimDAO().updateStatus(1, "DENIED");
        Map youngest = (Map) dao.agedClaims().get(0);
        assertEquals("0_30", youngest.get("bucket"));
        assertEquals(17.0, number(youngest, "claimCount"), 0.0);
    }

    @Test
    public void lossRatioByLineReportsEachLineInOrder() throws Exception {
        List rows = dao.lossRatioByLine();
        assertEquals(4, rows.size());
        String[] lines = { "AUTO", "COMMERCIAL_PROPERTY", "GENERAL_LIABILITY", "HOMEOWNERS" };
        double[] losses = { 40770.0, 38211.0, 34490.0, 37550.0 };
        for (int i = 0; i < lines.length; i++) {
            Map row = (Map) rows.get(i);
            assertEquals(lines[i], row.get("lineOfBusiness"));
            assertEquals(losses[i], number(row, "lossTotal"), 0.001);
            assertEquals(number(row, "lossTotal") / number(row, "premiumTotal"),
                    number(row, "lossRatio"), 1e-9);
        }
    }

    @Test
    public void lossRatioIncludesReserveChangesInLossTotal() throws Exception {
        double before = number((Map) dao.lossRatioByLine().get(0), "lossTotal");
        new ClaimDAO().updateReserve(1, 2001);
        double after = number((Map) dao.lossRatioByLine().get(0), "lossTotal");
        assertEquals(before + 1000.0, after, 0.001);
    }

    @Test
    public void claimCountsGroupsByStatusUsingRequestedOrdering() throws Exception {
        List rows = dao.claimCounts("status");
        assertEquals(5, rows.size());
        assertEquals("APPROVED", ((Map) rows.get(0)).get("status"));
        assertEquals("OPEN", ((Map) rows.get(4)).get("status"));
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(24.0, number((Map) rows.get(i), "count"), 0.0);
        }
        List descending = dao.claimCounts("status desc");
        assertEquals("OPEN", ((Map) descending.get(0)).get("status"));
    }

    @Test(expected = java.sql.SQLException.class)
    public void claimCountsRejectsInvalidOrdering() throws Exception {
        dao.claimCounts("no_such_column");
    }

    @Test
    public void reportsReturnConnectionsToPool() throws Exception {
        ConnectionPool.getInstance().closeAll();
        dao.openClaimsByAdjuster();
        dao.agedClaims();
        assertTrue(ConnectionPool.getInstance().size() == 1);
    }
}
