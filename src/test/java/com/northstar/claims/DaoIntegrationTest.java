package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.dao.ReportDAO;
import com.northstar.claims.model.Policy;
import com.northstar.claims.util.DatabaseBootstrap;

/** Exercises the JDBC gateways against a deterministic local HSQLDB file. */
public class DaoIntegrationTest {

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
    }

    @Test
    public void readsPolicyRows() throws Exception {
        Policy policy = new PolicyDAO().findById(9001);
        assertEquals(1000.0, policy.getPolicyLimit(), 0.0);
        assertEquals(100.0, policy.getDeductible(), 0.0);
    }

    @Test
    public void readsClaimsAndPayments() throws Exception {
        List claims = new ClaimDAO().findByStatus("OPEN");
        assertTrue(claims.size() > 0);
        List payments = new PaymentDAO().findByClaim(2);
        assertTrue(payments.size() > 0);
    }

    @Test
    public void countsSeededPolicies() throws Exception {
        assertEquals(42, new PolicyDAO().count());
    }

    @Test
    public void bucketsAgedClaimsFromSeed() throws Exception {
        List rows = new ReportDAO().agedClaims();
        assertEquals(4, rows.size());
        String[] buckets = {"0_30", "31_60", "61_90", "91_PLUS"};
        double[] counts = {18, 18, 18, 18};
        double[] reserves = {19086, 19056, 19026, 19116};
        for (int i = 0; i < buckets.length; i++) {
            Map row = (Map) rows.get(i);
            assertEquals(buckets[i], row.get("bucket"));
            assertEquals(counts[i], ((Double) row.get("claimCount")).doubleValue(), 0.0);
            assertEquals(reserves[i], ((Double) row.get("reserveTotal")).doubleValue(), 0.0);
        }
    }
}
