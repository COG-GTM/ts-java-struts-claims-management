package com.northstar.payment.persistence;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The PAYMENT and SETTLEMENT access of the slice, issuing the same SQL as
 * PaymentDAO, SettlementDAO.findByClaim and ClaimsActionSupport.nextId.
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R03 — the claim's settlement with the highest settlement_id
 *       (src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-110).</li>
 *   <li>PAY-R06 — payment_id is {@code max(payment_id) + 1} over the whole
 *       table, read when the request runs, with no locking
 *       (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75).</li>
 *   <li>PAY-R09, PAY-R10 — one insert of the nine columns with the values as
 *       given; the schema's NOT NULL and VARCHAR(20) constraints reject a
 *       missing payee or method and a method longer than 20 characters
 *       (src/main/java/com/northstar/claims/dao/PaymentDAO.java:139-164).</li>
 *   <li>PAY-R14, PAY-R19 — a claim's payments in payment_id order
 *       (PaymentDAO.java:99-118).</li>
 *   <li>PAY-R16, PAY-R17 — one payment by id, or nothing.</li>
 *   <li>PAY-R20 — {@code coalesce(sum(amount),0)} over every payment of the
 *       claim (PaymentDAO.java:121-136).</li>
 * </ul>
 */
@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** PAY-R03: the newest settlement of the claim, or empty. */
    public Optional<LatestSettlement> findLatestSettlement(int claimId) {
        return jdbc.query(
                "select * from SETTLEMENT where claim_id = ? order by settlement_id desc",
                rs -> rs.next()
                        ? Optional.of(new LatestSettlement(rs.getInt("settlement_id"),
                                rs.getDouble("settlement_amount")))
                        : Optional.empty(),
                claimId);
    }

    /** PAY-R06: the next key, allocated with no locking, as the legacy does. */
    public int nextId() {
        Integer id = jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class);
        return id == null ? 1 : id;
    }

    /** PAY-R09, PAY-R10: one insert; constraint failures propagate. */
    public void insert(StoredPayment payment) {
        jdbc.update("insert into PAYMENT (payment_id, claim_id, settlement_id, "
                + "payee_name, amount, payment_method, check_number, issued_date, "
                + "status) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                payment.paymentId(), payment.claimId(), payment.settlementId(),
                payment.payeeName(), payment.amount(), payment.paymentMethod(),
                payment.checkNumber(), Date.valueOf(payment.issuedDate()),
                payment.status());
    }

    /** PAY-R16, PAY-R17: the payment, or empty. */
    public Optional<StoredPayment> findById(int paymentId) {
        return jdbc.query("select * from PAYMENT where payment_id = ?",
                rs -> rs.next() ? Optional.of(read(rs)) : Optional.empty(),
                paymentId);
    }

    /** PAY-R14, PAY-R19: the claim's payments in payment_id order. */
    public List<StoredPayment> findByClaim(int claimId) {
        return jdbc.query("select * from PAYMENT where claim_id = ? order by payment_id",
                (rs, row) -> read(rs), claimId);
    }

    /** PAY-R20: the sum of every payment of the claim, whatever its status. */
    public double totalIssued(int claimId) {
        Double total = jdbc.queryForObject(
                "select coalesce(sum(amount),0) from PAYMENT where claim_id = ?",
                Double.class, claimId);
        return total == null ? 0 : total;
    }

    private StoredPayment read(ResultSet rs) throws SQLException {
        Date issued = rs.getDate("issued_date");
        return new StoredPayment(rs.getInt("payment_id"), rs.getInt("claim_id"),
                rs.getInt("settlement_id"), rs.getString("payee_name"),
                rs.getDouble("amount"), rs.getString("payment_method"),
                rs.getString("check_number"),
                issued == null ? null : issued.toLocalDate().toString(),
                rs.getString("status"));
    }
}
