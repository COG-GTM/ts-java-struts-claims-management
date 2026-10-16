package com.northstar.payment.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate port of PaymentDAO (src/main/java/com/northstar/claims/dao/PaymentDAO.java)
 * and of ClaimsActionSupport.nextId against the same PAYMENT table
 * (src/main/resources/db/schema.sql:106-118).
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R06 — the next id is {@code coalesce(max(payment_id),0)+1} over the
 *       whole table (ClaimsActionSupport.java:61-75) and insert writes exactly
 *       one row (PaymentDAO.insert).</li>
 *   <li>PAY-R14, PAY-R19 — {@code findByClaim} orders by payment_id
 *       (PaymentDAO.java:99-118).</li>
 *   <li>PAY-R16 — {@code findById} is PaymentDAO.findById.</li>
 *   <li>PAY-R20 — {@code totalIssued} is {@code coalesce(sum(amount),0)}
 *       (PaymentDAO.java:121-136).</li>
 * </ul>
 */
@Repository
public class PaymentRepository {
    private static final RowMapper<StoredPayment> ROW = (rs, i) -> new StoredPayment(
            rs.getInt("payment_id"), rs.getInt("claim_id"), rs.getInt("settlement_id"),
            rs.getString("payee_name"), rs.getDouble("amount"), rs.getString("payment_method"),
            rs.getString("check_number"), rs.getString("issued_date"), rs.getString("status"));

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** ClaimsActionSupport.java:61-75 with table PAYMENT. */
    public int nextId() {
        Integer next = jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class);
        return next == null ? 1 : next;
    }

    /** PaymentDAO.insert: one row, all nine columns, no defaults. */
    public void insert(StoredPayment payment) {
        jdbc.update("insert into PAYMENT (payment_id, claim_id, settlement_id, payee_name,"
                        + " amount, payment_method, check_number, issued_date, status)"
                        + " values (?,?,?,?,?,?,?,?,?)",
                payment.paymentId(), payment.claimId(), payment.settlementId(),
                payment.payeeName(), payment.amount(), payment.paymentMethod(),
                payment.checkNumber(), payment.issuedDate(), payment.status());
    }

    /** PaymentDAO.findById. */
    public Optional<StoredPayment> findById(int paymentId) {
        List<StoredPayment> rows = jdbc.query(
                "select * from PAYMENT where payment_id = ?", ROW, paymentId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** PaymentDAO.java:99-118. */
    public List<StoredPayment> findByClaim(int claimId) {
        return jdbc.query("select * from PAYMENT where claim_id = ? order by payment_id",
                ROW, claimId);
    }

    /** PaymentDAO.java:121-136. */
    public double totalIssued(int claimId) {
        Double total = jdbc.queryForObject(
                "select coalesce(sum(amount),0) from PAYMENT where claim_id = ?",
                Double.class, claimId);
        return total == null ? 0 : total;
    }
}
