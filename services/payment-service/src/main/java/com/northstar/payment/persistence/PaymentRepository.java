package com.northstar.payment.persistence;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The PAYMENT access of src/main/java/com/northstar/claims/dao/PaymentDAO.java
 * and the key allocation of ClaimsActionSupport.nextId, issuing the same SQL.
 */
@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** PAY-R09: the next key, allocated with no locking, as the legacy does (ClaimsActionSupport.java:61-75). */
    public int nextId() {
        Integer id = jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class);
        return id == null ? 1 : id;
    }

    /** PAY-R12: one insert of the nine columns (PaymentDAO.java:140-164). */
    public void insert(StoredPayment value) {
        jdbc.update("insert into PAYMENT "
                + "(payment_id,claim_id,settlement_id,payee_name,amount,"
                + "payment_method,check_number,issued_date,status) "
                + "values (?,?,?,?,?,?,?,?,?)",
                value.paymentId(), value.claimId(), value.settlementId(),
                value.payeeName(), value.amount(), value.paymentMethod(),
                value.checkNumber(), Date.valueOf(value.issuedDate()), value.status());
    }

    /** PAY-R21: one row by primary key, or empty (PaymentDAO.java:26-44). */
    public Optional<StoredPayment> findById(int paymentId) {
        return jdbc.query("select * from PAYMENT where payment_id = ?",
                rs -> rs.next() ? Optional.of(read(rs)) : Optional.empty(),
                paymentId);
    }

    /** PAY-R17: the claim's payments by ascending payment_id (PaymentDAO.java:100-119). */
    public List<StoredPayment> findByClaim(int claimId) {
        return jdbc.query("select * from PAYMENT where claim_id = ? order by payment_id",
                (rs, row) -> read(rs), claimId);
    }

    /** PAY-R26: the database sum, 0 when there are no rows (PaymentDAO.java:122-137). */
    public double totalIssued(int claimId) {
        Double total = jdbc.queryForObject(
                "select coalesce(sum(amount),0) from PAYMENT where claim_id = ?",
                Double.class, claimId);
        return total == null ? 0 : total;
    }

    private StoredPayment read(ResultSet rs) throws SQLException {
        Date issuedDate = rs.getDate("issued_date");
        return new StoredPayment(
                rs.getInt("payment_id"),
                rs.getInt("claim_id"),
                rs.getInt("settlement_id"),
                rs.getString("payee_name"),
                rs.getDouble("amount"),
                rs.getString("payment_method"),
                rs.getString("check_number"),
                issuedDate == null ? null : issuedDate.toLocalDate().toString(),
                rs.getString("status"));
    }
}
