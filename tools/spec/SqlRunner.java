import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * Applies precondition SQL to, or reads rows from, the legacy HSQLDB file
 * while Jetty is stopped. Used by tools/spec/payment_proof.py.
 * Usage: java -cp hsqldb.jar SqlRunner.java DB_PATH exec|query SQL...
 * Query rows are printed one per line, columns separated by TAB.
 */
public class SqlRunner {
    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:hsqldb:file:" + args[0], "SA", "");
             Statement statement = connection.createStatement()) {
            for (int i = 2; i < args.length; i++) {
                if ("exec".equals(args[1])) {
                    statement.execute(args[i]);
                    continue;
                }
                try (ResultSet results = statement.executeQuery(args[i])) {
                    ResultSetMetaData meta = results.getMetaData();
                    StringBuilder header = new StringBuilder("#");
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        header.append(c > 1 ? "\t" : "").append(meta.getColumnLabel(c));
                    }
                    System.out.println(header);
                    while (results.next()) {
                        StringBuilder row = new StringBuilder();
                        for (int c = 1; c <= meta.getColumnCount(); c++) {
                            row.append(c > 1 ? "\t" : "").append(results.getString(c));
                        }
                        System.out.println(row);
                    }
                }
            }
            statement.execute("SHUTDOWN");
        }
    }
}
