package com.northstar.claims.dao;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Resolves the database identity used by the claims application.
 *
 * Screens and gateways connect with the restricted application account, whose
 * password comes from the runtime environment or from the local credential
 * file written by the database bootstrap job. Nothing in the source tree
 * carries a usable password, and a session is refused when no credential is
 * configured or when the configured account is the database owner.
 */
public final class DatabaseCredentials {

    public static final String PATH_PROPERTY = "claims.db.path";
    public static final String USER_PROPERTY = "claims.db.user";
    public static final String PASSWORD_PROPERTY = "claims.db.password";
    public static final String OWNER_PASSWORD_PROPERTY =
            "claims.db.owner.password";

    public static final String DEFAULT_PATH = "target/db/northstar";
    public static final String DEFAULT_APPLICATION_USER = "CLAIMS_APP";
    public static final String OWNER_USER = "SA";

    private static final String USER_ENVIRONMENT = "CLAIMS_DB_USER";
    private static final String PASSWORD_ENVIRONMENT = "CLAIMS_DB_PASSWORD";
    private static final String OWNER_PASSWORD_ENVIRONMENT =
            "CLAIMS_DB_OWNER_PASSWORD";

    private static final String CREDENTIALS_SUFFIX = ".credentials";
    private static final String APPLICATION_PASSWORD_KEY =
            "claims.db.password";
    private static final String OWNER_PASSWORD_KEY =
            "claims.db.owner.password";

    private static final Set RESERVED_ACCOUNTS = new HashSet();

    static {
        RESERVED_ACCOUNTS.add("SA");
        RESERVED_ACCOUNTS.add("SYSTEM");
        RESERVED_ACCOUNTS.add("_SYSTEM");
        RESERVED_ACCOUNTS.add("DBA");
        RESERVED_ACCOUNTS.add("PUBLIC");
    }

    private DatabaseCredentials() {
    }

    /** Returns the configured file database location. */
    public static String databasePath() {
        return System.getProperty(PATH_PROPERTY, DEFAULT_PATH);
    }

    /** Returns the JDBC url for the configured file database. */
    public static String url() {
        return "jdbc:hsqldb:file:" + databasePath();
    }

    /** Returns the restricted account the application runs under. */
    public static String applicationUser() {
        String user = value(USER_PROPERTY, USER_ENVIRONMENT);
        return user == null ? DEFAULT_APPLICATION_USER
                : user.trim().toUpperCase();
    }

    /**
     * Returns the password of the restricted account.
     * The lookup order is system property, environment variable, then the
     * credential file written next to the database.
     */
    public static String applicationPassword() throws SQLException {
        String password = configuredApplicationPassword();
        if (password == null) {
            password = storedPassword(APPLICATION_PASSWORD_KEY);
        }
        if (password == null || password.length() == 0) {
            throw new SQLException("No claims database credential is"
                    + " configured; set " + PASSWORD_PROPERTY + " or run the"
                    + " database bootstrap job to provision the "
                    + applicationUser() + " account.");
        }
        return password;
    }

    /** Returns an explicitly configured application password, if any. */
    public static String configuredApplicationPassword() {
        return value(PASSWORD_PROPERTY, PASSWORD_ENVIRONMENT);
    }

    /** Returns the owner password known to this installation, if any. */
    public static String ownerPassword() {
        String password = value(OWNER_PASSWORD_PROPERTY,
                OWNER_PASSWORD_ENVIRONMENT);
        return password == null ? storedPassword(OWNER_PASSWORD_KEY) : password;
    }

    /** Opens a session for the restricted application account. */
    public static Connection openApplicationConnection() throws SQLException {
        String user = applicationUser();
        if (RESERVED_ACCOUNTS.contains(user)) {
            throw new SQLException("Refusing to open a claims database session"
                    + " as the administrative account " + user + ".");
        }
        return DriverManager.getConnection(url(), user, applicationPassword());
    }

    /**
     * Opens an owner session for maintenance jobs. Only the bootstrap and dump
     * utilities use this path; a fresh file database still has the empty
     * owner password that HSQLDB creates it with.
     */
    public static Connection openOwnerConnection() throws SQLException {
        String password = ownerPassword();
        return DriverManager.getConnection(url(), OWNER_USER,
                password == null ? "" : password);
    }

    /** Returns the credential file that belongs to the configured database. */
    public static File credentialsFile() {
        return new File(databasePath() + CREDENTIALS_SUFFIX);
    }

    /** Records the provisioned passwords for later runs of the application. */
    public static void writeCredentials(String applicationPassword,
            String ownerPassword) throws Exception {
        Properties stored = new Properties();
        if (applicationPassword != null) {
            stored.setProperty(APPLICATION_PASSWORD_KEY, applicationPassword);
        }
        if (ownerPassword != null) {
            stored.setProperty(OWNER_PASSWORD_KEY, ownerPassword);
        }
        File file = credentialsFile();
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        OutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stored.store(stream, "Generated claims database credentials");
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ignored) {
            }
        }
        restrictToOwner(file);
    }

    /** Returns a new random password for a provisioned account. */
    public static String generatePassword() {
        byte[] material = new byte[24];
        new SecureRandom().nextBytes(material);
        StringBuffer text = new StringBuffer();
        for (int i = 0; i < material.length; i++) {
            int value = material[i] & 0xff;
            if (value < 16) {
                text.append('0');
            }
            text.append(Integer.toHexString(value));
        }
        return text.toString();
    }

    private static String storedPassword(String key) {
        File file = credentialsFile();
        if (!file.isFile()) {
            return null;
        }
        InputStream stream = null;
        try {
            stream = new FileInputStream(file);
            Properties stored = new Properties();
            stored.load(stream);
            return stored.getProperty(key);
        } catch (Exception failure) {
            return null;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String value(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.trim().length() == 0) {
            value = System.getenv(environment);
        }
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }

    private static void restrictToOwner(File file) {
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }
}
