package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.AdjusterDAO;
import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.model.Adjuster;
import com.northstar.claims.util.DatabaseBootstrap;
import com.northstar.claims.util.PasswordHasher;

/** Pins the operator credential check against the seeded credential store. */
public class CredentialTest {

    @BeforeClass
    public static void seed() throws Exception {
        System.setProperty("claims.db.path", "target/db/test-northstar");
        DatabaseBootstrap.bootstrap(true);
    }

    @AfterClass
    public static void discardPooledConnections() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }

    @Test
    public void hashRoundTrips() {
        String stored = PasswordHasher.hash("correct horse");
        assertTrue(PasswordHasher.verify("correct horse", stored));
        assertFalse(PasswordHasher.verify("correct hors", stored));
        assertFalse(PasswordHasher.verify("", stored));
        assertFalse(PasswordHasher.verify(null, stored));
    }

    @Test
    public void hashIsSaltedAndNotReversible() {
        String first = PasswordHasher.hash("legacy1");
        String second = PasswordHasher.hash("legacy1");
        assertFalse(first.equals(second));
        assertEquals(-1, first.indexOf("legacy1"));
    }

    @Test
    public void rejectsPlaintextAndMalformedStoredValues() {
        assertFalse(PasswordHasher.verify("legacy1", "legacy1"));
        assertFalse(PasswordHasher.verify("legacy1", ""));
        assertFalse(PasswordHasher.verify("legacy1", "pbkdf2-sha1$1$zz$zz"));
    }

    @Test
    public void acceptsSeededOperator() throws Exception {
        Adjuster operator = new AdjusterDAO().authenticate("supervisor",
                "supervisor");
        assertNotNull(operator);
        assertEquals("supervisor", operator.getUsername());
    }

    @Test
    public void rejectsPrefixBackdoorCredentials() throws Exception {
        AdjusterDAO adjusters = new AdjusterDAO();
        assertNull(adjusters.authenticate("adjuster1", "legacyanything"));
        assertNull(adjusters.authenticate("adjuster99", "legacy99"));
        assertNull(adjusters.authenticate("adjusterX", "legacy"));
        assertNull(adjusters.authenticate("supervisor", "supervisor1"));
    }

    @Test
    public void rejectsMissingCredentials() throws Exception {
        AdjusterDAO adjusters = new AdjusterDAO();
        assertNull(adjusters.authenticate(null, null));
        assertNull(adjusters.authenticate("supervisor", null));
        assertNull(adjusters.authenticate("supervisor", ""));
    }

    @Test
    public void doesNotExposeStoredHashAsPlaintextPassword() throws Exception {
        Adjuster operator = new AdjusterDAO().authenticate("adjuster1",
                "legacy1");
        assertNotNull(operator);
        assertTrue(operator.getPasswordHash().startsWith("pbkdf2-sha1$"));
    }
}
