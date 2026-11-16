package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Claim;
import com.northstar.claims.util.DatabaseBootstrap;

/** Exercises the JDBC helpers in ClaimsActionSupport against the seeded test database. */
public class ClaimsActionSupportDatabaseTest {

    private final ClaimsActionSupport support = new ClaimsActionSupport() {
    };

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        ConnectionPool.getInstance().closeAll();
        DatabaseBootstrap.bootstrap(true);
    }

    @AfterClass
    public static void releasePool() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    @Test
    public void findClaimReturnsSeededClaim() {
        Claim claim = support.findClaim(2);
        assertEquals("CLM-00002", claim.getClaimNumber());
        assertEquals("INVESTIGATING", claim.getStatus());
        assertNull(support.findClaim(999999));
    }

    @Test
    public void scalarLookupsReadSeededRows() {
        assertEquals("CLM-00001", support.selectString(
                "select claim_number from CLAIM where claim_id = ?", 1));
        assertEquals("", support.selectString(
                "select claim_number from CLAIM where claim_id = ?", 999999));
        assertEquals(1002.0, support.selectAmount(
                "select reserve_amount from CLAIM where claim_id = ?", 2), 0.0);
        assertEquals(0.0, support.selectAmount(
                "select reserve_amount from CLAIM where claim_id = ?", 999999), 0.0);
    }

    @Test
    public void lookupsSwallowSqlErrors() {
        assertEquals("", support.selectString("select nope from NOWHERE where x = ?", 1));
        assertEquals(0.0, support.selectAmount("select nope from NOWHERE where x = ?", 1), 0.0);
        assertTrue(support.selectStrings("select nope from NOWHERE").isEmpty());
        assertEquals(0, support.countRows("NOWHERE"));
    }

    @Test
    public void listAndCountLookups() {
        List numbers = support.selectStrings(
                "select claim_number from CLAIM where claim_id <= 3 order by claim_id");
        assertEquals(3, numbers.size());
        assertEquals("CLM-00003", numbers.get(2));
        assertEquals(42, support.countRows("POLICY"));
        assertEquals(120, support.countRows("CLAIM"));
    }

    @Test
    public void nextIdAndUpdateWorkAgainstDatabase() throws Exception {
        assertEquals(9003, support.nextId("POLICY"));
        int changed = support.update(
                "update POLICY set status = 'LAPSED' where policy_id = 40");
        assertEquals(1, changed);
        assertEquals("LAPSED", support.selectString(
                "select status from POLICY where policy_id = ?", 40));
    }
}
