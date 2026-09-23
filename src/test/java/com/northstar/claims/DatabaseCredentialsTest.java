package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.northstar.claims.dao.DatabaseCredentials;
import com.northstar.claims.util.DatabaseBootstrap;

/** Verifies that database sessions no longer run as the HSQLDB super-user. */
public class DatabaseCredentialsTest {

    private static final String PATH = "target/db/credentials-northstar";

    private static String previousPath;

    @BeforeClass
    public static void seed() throws Exception {
        previousPath = System.getProperty(DatabaseCredentials.PATH_PROPERTY);
        System.setProperty(DatabaseCredentials.PATH_PROPERTY, PATH);
        System.clearProperty(DatabaseCredentials.USER_PROPERTY);
        System.clearProperty(DatabaseCredentials.PASSWORD_PROPERTY);
        System.clearProperty(DatabaseCredentials.OWNER_PASSWORD_PROPERTY);
        DatabaseCredentials.credentialsFile().delete();
        DatabaseBootstrap.bootstrap(true);
    }

    @AfterClass
    public static void restore() {
        if (previousPath == null) {
            System.clearProperty(DatabaseCredentials.PATH_PROPERTY);
        } else {
            System.setProperty(DatabaseCredentials.PATH_PROPERTY,
                    previousPath);
        }
    }

    @Test
    public void refusesTheHardcodedSuperUserCredential() {
        try {
            Connection connection = DriverManager.getConnection(
                    DatabaseCredentials.url(), "SA", "");
            connection.close();
            fail("SA with an empty password still opens a claims session");
        } catch (SQLException refused) {
            assertTrue(refused.getMessage().length() > 0);
        }
    }

    @Test
    public void applicationSessionsUseTheRestrictedAccount() throws Exception {
        Connection connection =
                DatabaseCredentials.openApplicationConnection();
        try {
            assertEquals("CLAIMS_APP",
                    connection.getMetaData().getUserName().toUpperCase());
            Statement statement = connection.createStatement();
            ResultSet results = statement.executeQuery(
                    "select count(*) from POLICY");
            assertTrue(results.next());
            assertTrue(results.getInt(1) > 0);
            results.close();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Test
    public void applicationAccountHasNoAdministrativePrivileges()
            throws Exception {
        Connection connection =
                DatabaseCredentials.openApplicationConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute("DROP TABLE PAYMENT");
                fail("the application account can drop claims tables");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().toLowerCase()
                                .indexOf("privilege") >= 0);
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Test
    public void refusesToOpenSessionsAsTheDatabaseOwner() {
        System.setProperty(DatabaseCredentials.USER_PROPERTY, "SA");
        try {
            DatabaseCredentials.openApplicationConnection().close();
            fail("an SA session was opened for the application");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().indexOf("administrative") >= 0);
        } finally {
            System.clearProperty(DatabaseCredentials.USER_PROPERTY);
        }
    }

    @Test
    public void failsClosedWithoutAConfiguredCredential() throws Exception {
        String path = System.getProperty(DatabaseCredentials.PATH_PROPERTY);
        System.setProperty(DatabaseCredentials.PATH_PROPERTY,
                "target/db/unprovisioned-northstar");
        try {
            DatabaseCredentials.applicationPassword();
            fail("a session was opened without a configured credential");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().indexOf("No claims database") >= 0);
        } finally {
            System.setProperty(DatabaseCredentials.PATH_PROPERTY, path);
        }
    }
}
