package com.northstar.claims.support;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Creates a deterministic file database from the checked-in SQL resources. */
public class DatabaseBootstrap {

    private static final Log log = LogFactory.getLog(DatabaseBootstrap.class);

    public static void main(String[] args) throws Exception {
        bootstrap(true);
    }

    /** Recreates the schema and applies every fixed seed statement. */
    public static void bootstrap(boolean reset) throws Exception {
        String path = System.getProperty("claims.db.path", "target/db/northstar");
        File parent = new File(path).getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Class.forName("org.hsqldb.jdbc.JDBCDriver");
        Connection connection = DriverManager.getConnection(
                "jdbc:hsqldb:file:" + path + ";hsqldb.write_delay=false", "SA", "");
        try {
            if (reset) {
                executeScript(connection, "db/schema.sql");
            }
            executeScript(connection, "db/seed.sql");
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

    /** Executes one statement for each semicolon-terminated script line. */
    private static void executeScript(Connection connection, String resource)
            throws Exception {
        BufferedReader reader = null;
        Statement statement = null;
        StringBuffer sql = new StringBuffer();
        try {
            reader = new BufferedReader(new FileReader(new File(
                    "../src/main/resources", resource)));
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
