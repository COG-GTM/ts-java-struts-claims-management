package com.northstar.claims.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Salted PBKDF2 password hashing for operator credentials.
 *
 * Stored values use the text form
 * <code>pbkdf2-sha1$iterations$saltHex$hashHex</code> so the ADJUSTER table
 * keeps a single character column and the iteration count can be raised
 * without a migration.
 */
public final class PasswordHasher {

    private static final Log log = LogFactory.getLog(PasswordHasher.class);

    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String PREFIX = "pbkdf2-sha1";
    private static final int ITERATIONS = 120000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 160;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** Produces a stored credential value for a new or rotated password. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + toHex(salt) + "$" + toHex(key);
    }

    /**
     * Verifies a candidate password against a stored credential value.
     * Comparison is constant time and unreadable or absent values never match.
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            log.warn("Rejecting credential with unsupported hash format");
            return false;
        }
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = fromHex(parts[2]);
            expected = fromHex(parts[3]);
        } catch (RuntimeException failure) {
            log.warn("Rejecting credential with unreadable hash value");
            return false;
        }
        if (iterations <= 0 || salt.length == 0 || expected.length == 0) {
            return false;
        }
        return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    }

    /**
     * Runs a derivation with throwaway material so a lookup miss costs the
     * same as a wrong password.
     */
    public static void verifyDummy(String password) {
        byte[] salt = new byte[SALT_BYTES];
        derive(password == null ? "" : password, salt, ITERATIONS);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt,
                iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec).getEncoded();
        } catch (Exception failure) {
            throw new IllegalStateException("Password hashing unavailable",
                    failure);
        } finally {
            spec.clearPassword();
        }
    }

    private static String toHex(byte[] value) {
        StringBuffer text = new StringBuffer(value.length * 2);
        for (int i = 0; i < value.length; i++) {
            int b = value[i] & 0xff;
            if (b < 0x10) {
                text.append('0');
            }
            text.append(Integer.toHexString(b));
        }
        return text.toString();
    }

    private static byte[] fromHex(String text) {
        if (text.length() % 2 != 0) {
            throw new IllegalArgumentException("Odd length hex value");
        }
        byte[] value = new byte[text.length() / 2];
        for (int i = 0; i < value.length; i++) {
            value[i] = (byte) Integer.parseInt(
                    text.substring(i * 2, i * 2 + 2), 16);
        }
        return value;
    }
}
