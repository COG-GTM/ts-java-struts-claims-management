package com.northstar.claims.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2 password hashing for operator credentials.
 *
 * Stored values use the text form
 * <code>pbkdf2-sha1$iterations$saltHex$hashHex</code> so the ADJUSTER table
 * keeps a single character column and no credential material is embedded in
 * application source.
 */
public final class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    public static final int ITERATIONS = 120000;
    public static final int SALT_BYTES = 16;
    public static final int KEY_BITS = 160;

    private static final String PREFIX = "pbkdf2-sha1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** Hashes a password with a freshly generated random salt. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return hash(password, salt, ITERATIONS);
    }

    /** Hashes a password with a caller supplied salt and work factor. */
    public static String hash(String password, byte[] salt, int iterations) {
        byte[] derived = derive(password, salt, iterations);
        return PREFIX + "$" + iterations + "$" + toHex(salt) + "$"
                + toHex(derived);
    }

    /**
     * Verifies a candidate password against a stored hash. Values that are not
     * in the supported hash format never verify, so legacy plaintext rows
     * cannot authenticate.
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = fromHex(parts[2]);
            expected = fromHex(parts[3]);
        } catch (RuntimeException malformed) {
            return false;
        }
        if (iterations <= 0 || salt.length == 0 || expected.length == 0) {
            return false;
        }
        return MessageDigest.isEqual(expected,
                derive(password, salt, iterations));
    }

    private static byte[] derive(String password, byte[] salt,
            int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt,
                iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec).getEncoded();
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Password hashing is unavailable", failure);
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
