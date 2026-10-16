// Read-only HSQLDB probe for SPEC-PAY-001 evidence. Run after Jetty has stopped:
//   java -cp ~/.m2/repository/org/hsqldb/hsqldb/2.7.3/hsqldb-2.7.3.jar tools/spec/PayDbRead.java
import java.sql.*;
public class PayDbRead {
  public static void main(String[] a) throws Exception {
    Connection c = DriverManager.getConnection("jdbc:hsqldb:file:target/db/northstar;readonly=true", "SA", "");
    Statement st = c.createStatement();
    ResultSet r = st.executeQuery("select payment_id, claim_id, settlement_id, '[' || payee_name || ']' as payee, amount, '[' || payment_method || ']' as method, check_number, issued_date, status from PAYMENT where payment_id > 60 order by payment_id");
    while (r.next()) { StringBuilder b = new StringBuilder(); for (int i = 1; i <= 9; i++) b.append(r.getString(i)).append(" | "); System.out.println(b); }
    r = st.executeQuery("select count(*), max(payment_id) from PAYMENT"); r.next(); System.out.println("count=" + r.getInt(1) + " max=" + r.getInt(2));
    r = st.executeQuery("select settlement_id, claim_id, settlement_amount from SETTLEMENT where claim_id in (118,119,120) order by settlement_id"); while (r.next()) System.out.println("SETTLEMENT " + r.getInt(1) + " claim " + r.getInt(2) + " amount " + r.getString(3));
    r = st.executeQuery("select count(*) from PAYMENT where claim_id = 118"); r.next(); System.out.println("claim118 count=" + r.getInt(1));
    c.close();
  }
}
