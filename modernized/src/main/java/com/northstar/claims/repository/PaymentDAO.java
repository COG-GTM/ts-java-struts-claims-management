package com.northstar.claims.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.domain.Payment;


/**
 * JDBC gateway for PAYMENT records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
@org.springframework.stereotype.Repository
public class PaymentDAO {

    private final org.springframework.jdbc.core.JdbcOperations jdbc;

    public PaymentDAO(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        this.jdbc = template.getJdbcOperations();
    }

    private static final Log log = LogFactory.getLog(PaymentDAO.class);

    /** Loads one row by its numeric primary key. */
    public Payment findById(int id) throws SQLException {
        return jdbc.query("select * from PAYMENT where payment_id = ?", ps -> {
            ps.setInt(1, id);
        }, rs -> {
            if (rs.next()) {
                return read(rs);
            }
            return null;
        });
    }

    /** Returns every row in primary-key order. */
    public List findAll() throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select * from PAYMENT order by payment_id", rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Counts rows for summary pages. */
    public int count() throws SQLException {
        return jdbc.query("select count(*) from PAYMENT", rs -> {
            return rs.next() ? rs.getInt(1) : 0;
        });
    }

    /** Deletes a row when an administrator removes a record. */
    public void delete(int id) throws SQLException {
        jdbc.update("delete from PAYMENT where payment_id = ?", ps -> {
            ps.setInt(1, id);
        });
    }

    /** Returns payments issued for a claim. */
    public List findByClaim(int claimId) throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select * from PAYMENT where claim_id = ? order by payment_id", ps -> {
            ps.setInt(1, claimId);
        }, rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Calculates total issued money for a claim. */
    public double totalIssued(int claimId) throws SQLException {
        return jdbc.query("select coalesce(sum(amount),0) from PAYMENT where claim_id = ?", ps -> {
            ps.setInt(1, claimId);
        }, rs -> {
            return rs.next() ? rs.getDouble(1) : 0;
        });
    }

    /** Inserts a payment record after a check is approved. */
    public void insert(Payment value) throws SQLException {
        jdbc.update(
                    "insert into PAYMENT "
                    + "(payment_id,claim_id,settlement_id,payee_name,amount,"
                    + "payment_method,check_number,issued_date,status) "
                    + "values (?,?,?,?,?,?,?,?,?)", ps -> {
            ps.setInt(1, value.getPaymentId());
            ps.setInt(2, value.getClaimId());
            ps.setInt(3, value.getSettlementId());
            ps.setString(4, value.getPayeeName());
            ps.setDouble(5, value.getAmount());
            ps.setString(6, value.getPaymentMethod());
            ps.setString(7, value.getCheckNumber());
            ps.setString(8, value.getIssuedDate());
            ps.setString(9, value.getStatus());
        });
    }

    /** Converts a result set row into the corresponding mutable bean. */
    private Payment read(ResultSet rs) throws SQLException {
        Payment value = new Payment();
        value.setPaymentId(rs.getInt("payment_id"));
        value.setClaimId(rs.getInt("claim_id"));
        value.setSettlementId(rs.getInt("settlement_id"));
        value.setPayeeName(rs.getString("payee_name"));
        value.setAmount(rs.getDouble("amount"));
        value.setPaymentMethod(rs.getString("payment_method"));
        value.setCheckNumber(rs.getString("check_number"));
        value.setIssuedDate(rs.getString("issued_date"));
        value.setStatus(rs.getString("status"));
        return value;
    }

    /** Logs the statement boundary used by old production diagnostics. */
    private void trace(String text) {
        log.debug(text);
        System.out.println(text);
    }
}
