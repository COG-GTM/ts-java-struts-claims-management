package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.dao.ReportDAO;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Policy;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Checks the operations reports against totals derived from the claim rows. */
public class ReportDaoTest {

    private final ReportDAO reports = new ReportDAO();

    @BeforeClass
    public static void seedDatabase() throws Exception {
        TestDatabase.reset();
    }

    @AfterClass
    public static void releaseDatabase() throws Exception {
        TestDatabase.release();
    }

    @Test
    public void openClaimsByAdjusterMatchesOpenAndInvestigatingClaims() throws Exception {
        Map counts = new TreeMap();
        Map reserves = new TreeMap();
        List claims = new ClaimDAO().findAll();
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = (Claim) claims.get(i);
            if ("OPEN".equals(claim.getStatus()) || "INVESTIGATING".equals(claim.getStatus())) {
                String key = claim.getAssignedAdjuster();
                Integer count = (Integer) counts.get(key);
                counts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                Double reserve = (Double) reserves.get(key);
                reserves.put(key, new Double((reserve == null ? 0 : reserve.doubleValue())
                        + claim.getReserveAmount()));
            }
        }

        List rows = reports.openClaimsByAdjuster();
        assertEquals(counts.size(), rows.size());
        Object[] adjusters = counts.keySet().toArray();
        for (int i = 0; i < rows.size(); i++) {
            Map row = (Map) rows.get(i);
            assertEquals(adjusters[i], row.get("adjuster"));
            assertEquals(((Integer) counts.get(adjusters[i])).doubleValue(),
                    ((Double) row.get("openCount")).doubleValue(), 0.001);
            assertEquals(((Double) reserves.get(adjusters[i])).doubleValue(),
                    ((Double) row.get("reserveTotal")).doubleValue(), 0.001);
        }
    }

    @Test
    public void lossRatioCoversEveryLineInOrder() throws Exception {
        TreeSet lines = new TreeSet();
        List policies = new PolicyDAO().findAll();
        for (int i = 0; i < policies.size(); i++) {
            lines.add(((Policy) policies.get(i)).getLineOfBusiness());
        }

        List rows = reports.lossRatioByLine();
        assertEquals(lines.size(), rows.size());
        Object[] expected = lines.toArray();
        for (int i = 0; i < rows.size(); i++) {
            Map row = (Map) rows.get(i);
            assertEquals(expected[i], row.get("lineOfBusiness"));
            double premium = ((Double) row.get("premiumTotal")).doubleValue();
            double loss = ((Double) row.get("lossTotal")).doubleValue();
            assertTrue(premium > 0);
            assertTrue(loss >= 0);
            assertEquals(loss / premium, ((Double) row.get("lossRatio")).doubleValue(), 0.0001);
        }
    }

    @Test
    public void agedClaimsBucketsActiveClaimsByReportedAge() throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long asOf = format.parse("2019-04-01").getTime();
        Map counts = new TreeMap();
        Map reserves = new TreeMap();
        List claims = new ClaimDAO().findAll();
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = (Claim) claims.get(i);
            if ("CLOSED".equals(claim.getStatus()) || "DENIED".equals(claim.getStatus())) {
                continue;
            }
            long days = (asOf - format.parse(claim.getReportedDate()).getTime()) / 86400000L;
            String bucket = days <= 30 ? "0_30" : days <= 60 ? "31_60" : days <= 90 ? "61_90" : "91_PLUS";
            Integer count = (Integer) counts.get(bucket);
            counts.put(bucket, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            Double reserve = (Double) reserves.get(bucket);
            reserves.put(bucket, new Double((reserve == null ? 0 : reserve.doubleValue())
                    + claim.getReserveAmount()));
        }
        assertTrue(counts.size() > 1);

        List rows = reports.agedClaims();
        assertEquals(counts.size(), rows.size());
        Object[] buckets = counts.keySet().toArray();
        for (int i = 0; i < rows.size(); i++) {
            Map row = (Map) rows.get(i);
            assertEquals(buckets[i], row.get("bucket"));
            assertEquals(((Integer) counts.get(buckets[i])).doubleValue(),
                    ((Double) row.get("claimCount")).doubleValue(), 0.001);
            assertEquals(((Double) reserves.get(buckets[i])).doubleValue(),
                    ((Double) row.get("reserveTotal")).doubleValue(), 0.001);
        }
    }

    @Test
    public void claimCountsGroupsByStatusInRequestedOrder() throws Exception {
        List rows = reports.claimCounts("status");
        String[] statuses = { "APPROVED", "CLOSED", "DENIED", "INVESTIGATING", "OPEN" };
        assertEquals(statuses.length, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map row = (Map) rows.get(i);
            assertEquals(statuses[i], row.get("status"));
            assertEquals(24.0, ((Double) row.get("count")).doubleValue(), 0.001);
        }
    }

    @Test
    public void claimCountsHonoursDescendingOrder() throws Exception {
        List rows = reports.claimCounts("status desc");
        assertEquals("OPEN", ((Map) rows.get(0)).get("status"));
        assertEquals("APPROVED", ((Map) rows.get(rows.size() - 1)).get("status"));
    }

    @Test
    public void claimCountsRejectsUnknownOrderColumn() throws Exception {
        try {
            reports.claimCounts("no_such_column");
            fail("expected SQLException");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }
}
