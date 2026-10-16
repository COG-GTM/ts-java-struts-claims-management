import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * Read-only dump of the PAYMENT and SETTLEMENT rows the payment live proof
 * touches, printed as JSON. Run only while Jetty is stopped (HSQLDB file lock):
 * java -cp hsqldb-2.7.3.jar tools/spec/PayDbRead.java target/db/northstar
 */
public class PayDbRead {

    private static final String[][] QUERIES = {
        {"payment_rows_after_seed",
         "select * from PAYMENT where payment_id > 60 or claim_id = 1 order by payment_id"},
        {"payment_totals",
         "select count(*) as payment_count, max(payment_id) as max_payment_id from PAYMENT"},
        {"settlements_used",
         "select settlement_id, claim_id, settlement_amount from SETTLEMENT"
         + " where claim_id in (1, 119, 120) order by settlement_id"},
        {"payment_column_types",
         "select column_name, type_name, column_size, is_nullable from"
         + " information_schema.system_columns where table_name = 'PAYMENT'"
         + " order by ordinal_position"},
    };

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "target/db/northstar";
        try (Connection c = DriverManager.getConnection(
                "jdbc:hsqldb:file:" + path + ";readonly=true", "SA", "")) {
            StringBuilder out = new StringBuilder("{\n");
            for (int q = 0; q < QUERIES.length; q++) {
                out.append("  \"").append(QUERIES[q][0]).append("\": {\"sql\": ")
                   .append(quote(QUERIES[q][1])).append(", \"rows\": [");
                try (Statement s = c.createStatement();
                     ResultSet r = s.executeQuery(QUERIES[q][1])) {
                    ResultSetMetaData m = r.getMetaData();
                    boolean first = true;
                    while (r.next()) {
                        out.append(first ? "\n    {" : ",\n    {");
                        first = false;
                        for (int i = 1; i <= m.getColumnCount(); i++) {
                            if (i > 1) {
                                out.append(", ");
                            }
                            out.append(quote(m.getColumnLabel(i).toLowerCase()))
                               .append(": ").append(quote(r.getString(i)));
                        }
                        out.append("}");
                    }
                }
                out.append("\n  ]}").append(q + 1 < QUERIES.length ? ",\n" : "\n");
            }
            System.out.print(out.append("}\n"));
        }
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
