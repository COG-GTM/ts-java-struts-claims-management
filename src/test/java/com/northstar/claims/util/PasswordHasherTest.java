package com.northstar.claims.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

/** Verifies the salted PBKDF2 credential hashing helper. */
public class PasswordHasherTest {

    @Test
    public void verifiesMatchingPassword() {
        String stored = PasswordHasher.hash("correct horse");
        assertTrue(PasswordHasher.verify("correct horse", stored));
    }

    @Test
    public void rejectsWrongPassword() {
        String stored = PasswordHasher.hash("correct horse");
        assertFalse(PasswordHasher.verify("correct hors", stored));
        assertFalse(PasswordHasher.verify("", stored));
        assertFalse(PasswordHasher.verify(null, stored));
    }

    @Test
    public void saltsEachHash() {
        assertNotEquals(PasswordHasher.hash("same"),
                PasswordHasher.hash("same"));
    }

    @Test
    public void rejectsUnhashedStoredValues() {
        assertFalse(PasswordHasher.verify("legacy1", "legacy1"));
        assertFalse(PasswordHasher.verify("legacy1", null));
        assertFalse(PasswordHasher.verify("legacy1", "pbkdf2-sha1$0$$"));
        assertFalse(PasswordHasher.verify("legacy1",
                "pbkdf2-sha1$abc$00$00"));
    }
}
