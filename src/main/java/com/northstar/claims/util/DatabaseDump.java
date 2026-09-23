package com.northstar.claims.util;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.dao.DatabaseCredentials;

/** Writes a stable text representation of every table for reset comparisons. */
public class DatabaseDump {

    private static final Log log = LogFactory.getLog(DatabaseDump.class);
    private static final String[] TABLES = { "ADJUSTER", "CLAIM", "CLAIM_NOTE",
            "COVERAGE", "INSURED_PARTY", "PAYMENT", "POLICY",
            "RESERVE_HISTORY", "SETTLEMENT" };

    public static void main(String[] args) throws Exception {
        String output = args.length == 0 ? "target/database-dump.txt" : args[0];
        dump(output);
    }

    public static void dump(String output) throws Exception {
        Connection connection = DatabaseCredentials.openOwnerConnection();
        PrintWriter writer = new PrintWriter(new FileWriter(output));
        try {
            for (int i = 0; i < TABLES.length; i++) {
                dumpTable(connection, writer, TABLES[i]);
            }
        } finally {
            try { writer.close(); } catch (Exception e) {}
            try { connection.createStatement().execute("shutdown"); } catch (Exception e) {}
            try { connection.close(); } catch (Exception e) {}
        }
    }

    private static void dumpTable(Connection connection, PrintWriter writer,
            String table) throws Exception {
        Statement statement = null;
        ResultSet results = null;
        try {
            statement = connection.createStatement();
            results = statement.executeQuery("select * from " + table
                    + " order by 1");
            writer.println(table);
            ResultSetMetaData meta = results.getMetaData();
            while (results.next()) {
                StringBuffer row = new StringBuffer();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    if (i > 1) {
                        row.append('|');
                    }
                    row.append(results.getString(i));
                }
                writer.println(row.toString());
            }
        } finally {
            try { results.close(); } catch (Exception e) {}
            try { statement.close(); } catch (Exception e) {}
        }
    }
}
