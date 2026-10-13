package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.dao.ReportDAO;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies the aggregate rows behind the management report screens. */
public class ReportDaoIntegrationTest {

    private final ReportDAO dao = new ReportDAO();

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

    private static double number(Map row, String key) {
        return ((Double) row.get(key)).doubleValue();
    }

    @Test
    public void openClaimsByAdjusterGroupsOpenAndInvestigating() throws Exception {
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
    public void lossRatioByLineCoversEveryLineInOrder() throws Exception {
        List rows = dao.lossRatioByLine();
        assertEquals(4, rows.size());
        String previous = "";
        for (int i = 0; i < rows.size(); i++) {
            Map row = (Map) rows.get(i);
            String line = (String) row.get("lineOfBusiness");
            assertTrue(line.compareTo(previous) > 0);
            previous = line;
            double premium = number(row, "premiumTotal");
            double loss = number(row, "lossTotal");
            assertTrue(premium > 0);
            assertTrue(loss >= 0);
            assertEquals(loss / premium, number(row, "lossRatio"), 1e-9);
        }
        assertEquals("AUTO", ((Map) rows.get(0)).get("lineOfBusiness"));
    }

    @Test
    public void agedClaimsBucketsActiveClaimsByReportedDate() throws Exception {
        List rows = dao.agedClaims();
        Map buckets = new HashMap();
        double total = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map row = (Map) rows.get(i);
            String bucket = (String) row.get("bucket");
            assertTrue(bucket.equals("0_30") || bucket.equals("31_60")
                    || bucket.equals("61_90") || bucket.equals("91_PLUS"));
            buckets.put(bucket, row.get("claimCount"));
            total += number(row, "claimCount");
            assertTrue(number(row, "reserveTotal") > 0);
        }
        assertEquals(72.0, total, 0.0);
        assertTrue(buckets.containsKey("0_30"));
        assertTrue(buckets.containsKey("91_PLUS"));
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
    public void claimCountsRejectsUnknownOrderColumn() throws Exception {
        dao.claimCounts("no_such_column");
    }
}
