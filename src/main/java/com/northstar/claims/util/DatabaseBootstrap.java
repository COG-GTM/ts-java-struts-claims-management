package com.northstar.claims.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.dao.DatabaseCredentials;

/** Creates a deterministic file database from the checked-in SQL resources. */
public class DatabaseBootstrap {

    private static final Log log = LogFactory.getLog(DatabaseBootstrap.class);

    public static void main(String[] args) throws Exception {
        bootstrap(true);
    }

    /** Recreates the schema and applies every fixed seed statement. */
    public static void bootstrap(boolean reset) throws Exception {
        String path = DatabaseCredentials.databasePath();
        File parent = new File(path).getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Class.forName("org.hsqldb.jdbc.JDBCDriver");
        Connection connection = DatabaseCredentials.openOwnerConnection();
        try {
            if (reset) {
                executeScript(connection, "db/schema.sql");
            }
            executeScript(connection, "db/seed.sql");
            provisionAccounts(connection);
            connection.commit();
            log.info("Database bootstrap completed for " + path);
        } finally {
            try {
                connection.createStatement().execute("shutdown");
            } catch (Exception ignored) {
            }
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Creates the restricted application account with data privileges only and
     * moves the database owner off its initial empty password. The generated
     * passwords are written to the local credential file so that the servlet
     * tier can open sessions without a credential in the source tree.
     */
    private static void provisionAccounts(Connection connection)
            throws Exception {
        String user = DatabaseCredentials.applicationUser();
        String password = DatabaseCredentials.configuredApplicationPassword();
        boolean generated = password == null;
        if (generated) {
            password = DatabaseCredentials.generatePassword();
        }
        Statement statement = connection.createStatement();
        try {
            if (userExists(connection, user)) {
                statement.execute("ALTER USER \"" + user
                        + "\" SET PASSWORD '" + password + "'");
            } else {
                statement.execute("CREATE USER \"" + user + "\" PASSWORD '"
                        + password + "'");
            }
            statement.execute("ALTER USER \"" + user
                    + "\" SET INITIAL SCHEMA PUBLIC");
            List tables = publicTables(connection);
            for (int i = 0; i < tables.size(); i++) {
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON"
                        + " TABLE PUBLIC.\"" + tables.get(i) + "\" TO \""
                        + user + "\"");
            }
            String ownerPassword = DatabaseCredentials.ownerPassword();
            if (ownerPassword == null) {
                ownerPassword = DatabaseCredentials.generatePassword();
                statement.execute("ALTER USER \""
                        + DatabaseCredentials.OWNER_USER
                        + "\" SET PASSWORD '" + ownerPassword + "'");
            }
            DatabaseCredentials.writeCredentials(generated ? password : null,
                    ownerPassword);
            log.info("Provisioned restricted database account " + user);
        } finally {
            try { statement.close(); } catch (Exception ignored) {}
        }
    }

    /** Reports whether the named account already exists in the database. */
    private static boolean userExists(Connection connection, String user)
            throws Exception {
        Statement statement = null;
        ResultSet results = null;
        try {
            statement = connection.createStatement();
            results = statement.executeQuery("select user_name from"
                    + " information_schema.system_users");
            while (results.next()) {
                if (user.equalsIgnoreCase(results.getString(1))) {
                    return true;
                }
            }
            return false;
        } finally {
            try { results.close(); } catch (Exception ignored) {}
            try { statement.close(); } catch (Exception ignored) {}
        }
    }

    /** Lists the application tables the restricted account may work with. */
    private static List publicTables(Connection connection) throws Exception {
        List tables = new ArrayList();
        ResultSet results = null;
        try {
            results = connection.getMetaData().getTables(null, "PUBLIC", "%",
                    new String[] { "TABLE" });
            while (results.next()) {
                tables.add(results.getString("TABLE_NAME"));
            }
        } finally {
            try { results.close(); } catch (Exception ignored) {}
        }
        return tables;
    }

    /** Executes one statement for each semicolon-terminated script line. */
    private static void executeScript(Connection connection, String resource)
            throws Exception {
        BufferedReader reader = null;
        Statement statement = null;
        StringBuffer sql = new StringBuffer();
        try {
            reader = new BufferedReader(new FileReader(new File(
                    "src/main/resources", resource)));
            statement = connection.createStatement();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0 || line.trim().startsWith("--")) {
                    continue;
                }
                sql.append(line);
                if (line.trim().endsWith(";")) {
                    String text = sql.substring(0, sql.length() - 1);
                    statement.executeUpdate(text);
                    sql.setLength(0);
                }
            }
        } finally {
            try { statement.close(); } catch (Exception e) {}
            try { reader.close(); } catch (Exception e) {}
        }
    }
}
