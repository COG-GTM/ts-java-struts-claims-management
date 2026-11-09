package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.ReportDAO;

/** Verifies the aggregate SQL behind the operations reports against seed data. */
public class ReportDAOTest {

    private final ReportDAO dao = new ReportDAO();

    @BeforeClass
    public static void seed() throws Exception {
        TestDatabase.reset();
    }

    @AfterClass
    public static void release() throws Exception {
        TestDatabase.release();
    }

    private static Map row(List rows, int index) {
        return (Map) rows.get(index);
    }

    private static double number(Map row, String key) {
        return ((Double) row.get(key)).doubleValue();
    }

    @Test
    public void openClaimsByAdjusterGroupsOpenAndInvestigatingClaims() throws Exception {
        List rows = dao.openClaimsByAdjuster();
        assertEquals(2, rows.size());
        assertEquals("adjuster1", row(rows, 0).get("adjuster"));
        assertEquals(24.0, number(row(rows, 0), "openCount"), 0.0);
        assertEquals(25404.0, number(row(rows, 0), "reserveTotal"), 0.001);
        assertEquals("adjuster2", row(rows, 1).get("adjuster"));
        assertEquals(24.0, number(row(rows, 1), "openCount"), 0.0);
        assertEquals(25428.0, number(row(rows, 1), "reserveTotal"), 0.001);
    }

    @Test
    public void lossRatioByLineCombinesReservesAndPaymentsOverPremium() throws Exception {
        List rows = dao.lossRatioByLine();
        assertEquals(4, rows.size());
        Map auto = row(rows, 0);
        assertEquals("AUTO", auto.get("lineOfBusiness"));
        assertEquals(90700.0, number(auto, "premiumTotal"), 0.001);
        assertEquals(40770.0, number(auto, "lossTotal"), 0.001);
        assertEquals(40770.0 / 90700.0, number(auto, "lossRatio"), 1e-9);
        assertEquals("COMMERCIAL_PROPERTY", row(rows, 1).get("lineOfBusiness"));
        assertEquals("GENERAL_LIABILITY", row(rows, 2).get("lineOfBusiness"));
        Map homeowners = row(rows, 3);
        assertEquals("HOMEOWNERS", homeowners.get("lineOfBusiness"));
        assertEquals(37550.0, number(homeowners, "lossTotal"), 0.001);
        assertEquals(37550.0 / 93700.0, number(homeowners, "lossRatio"), 1e-9);
    }

    @Test
    public void agedClaimsBucketsActiveClaimsByReportedDate() throws Exception {
        List rows = dao.agedClaims();
        assertEquals(4, rows.size());
        String[] buckets = { "0_30", "31_60", "61_90", "91_PLUS" };
        double[] reserves = { 19086.0, 19056.0, 19026.0, 19116.0 };
        for (int i = 0; i < buckets.length; i++) {
            assertEquals(buckets[i], row(rows, i).get("bucket"));
            assertEquals(18.0, number(row(rows, i), "claimCount"), 0.0);
            assertEquals(reserves[i], number(row(rows, i), "reserveTotal"), 0.001);
        }
    }

    @Test
    public void claimCountsGroupsByStatusInRequestedOrder() throws Exception {
        List rows = dao.claimCounts("status");
        assertEquals(5, rows.size());
        assertEquals("APPROVED", row(rows, 0).get("status"));
        assertEquals("OPEN", row(rows, 4).get("status"));
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(24.0, number(row(rows, i), "count"), 0.0);
        }
    }

    @Test
    public void closingAClaimRemovesItFromOpenAndAgedReports() throws Exception {
        try {
            new ClaimDAO().updateStatus(1, "CLOSED");
            List open = dao.openClaimsByAdjuster();
            assertEquals(23.0, number(row(open, 0), "openCount"), 0.0);
            assertEquals(25404.0 - 1001.0, number(row(open, 0), "reserveTotal"), 0.001);
            double aged = 0;
            List rows = dao.agedClaims();
            for (int i = 0; i < rows.size(); i++) {
                aged += number(row(rows, i), "claimCount");
            }
            assertEquals(71.0, aged, 0.0);
        } finally {
            new ClaimDAO().updateStatus(1, "OPEN");
        }
        assertTrue(dao.openClaimsByAdjuster().size() == 2);
    }
}
