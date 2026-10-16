import java.sql.*;

/**
 * Read-only dump of the PAYMENT rows written by tools/analysis/payments_live.py.
 * Run after stopping Jetty:
 *   java -cp ~/.m2/repository/org/hsqldb/hsqldb/2.7.3/hsqldb-2.7.3.jar tools/analysis/PayDbDump.java
 */
public class PayDbDump {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:hsqldb:file:target/db/northstar;readonly=true", "SA", "");
             Statement s = c.createStatement()) {
            ResultSet r = s.executeQuery("select payment_id, claim_id, settlement_id, payee_name, amount,"
                    + " payment_method, check_number, issued_date, status from PAYMENT"
                    + " where payment_id > 60 order by payment_id");
            while (r.next()) {
                System.out.println(r.getInt(1) + "|" + r.getInt(2) + "|" + r.getInt(3) + "|" + r.getString(4)
                        + "|" + Double.toString(r.getDouble(5)) + "|" + r.getString(6) + "|" + r.getString(7)
                        + "|" + r.getString(8) + "|" + r.getString(9));
            }
            r = s.executeQuery("select max(settlement_id), count(*) from SETTLEMENT where claim_id = 119");
            r.next();
            System.out.println("settlement.claim119.max=" + r.getInt(1) + " count=" + r.getInt(2));
        }
    }
}
