package com.northstar.claims.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.model.Payment;


/**
 * JDBC gateway for PAYMENT records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
public class PaymentDAO {

    private static final Log log = LogFactory.getLog(PaymentDAO.class);

    /** Loads one row by its numeric primary key. */
    public Payment findById(int id) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select * from PAYMENT where payment_id = ?");
            ps.setInt(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {
                return read(rs);
            }
            return null;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Returns every row in primary-key order. */
    public List findAll() throws SQLException {
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            st = conn.createStatement();
            rs = st.executeQuery("select * from PAYMENT order by payment_id");
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { st.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Counts rows for summary pages. */
    public int count() throws SQLException {
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            st = conn.createStatement();
            rs = st.executeQuery("select count(*) from PAYMENT");
            return rs.next() ? rs.getInt(1) : 0;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { st.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Deletes a row when an administrator removes a record. */
    public void delete(int id) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("delete from PAYMENT where payment_id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
        } finally {
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Returns payments issued for a claim. */
    public List findByClaim(int claimId) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select * from PAYMENT where claim_id = ? order by payment_id");
            ps.setInt(1, claimId);
            rs = ps.executeQuery();
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Calculates total issued money for a claim. */
    public double totalIssued(int claimId) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select coalesce(sum(amount),0) from PAYMENT where claim_id = ?");
            ps.setInt(1, claimId);
            rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Inserts a payment record after a check is approved. */
    public void insert(Payment value) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement(
                    "insert into PAYMENT "
                    + "(payment_id,claim_id,settlement_id,payee_name,amount,"
                    + "payment_method,check_number,issued_date,status) "
                    + "values (?,?,?,?,?,?,?,?,?)");
            ps.setInt(1, value.getPaymentId());
            ps.setInt(2, value.getClaimId());
            ps.setInt(3, value.getSettlementId());
            ps.setString(4, value.getPayeeName());
            ps.setDouble(5, value.getAmount());
            ps.setString(6, value.getPaymentMethod());
            ps.setString(7, value.getCheckNumber());
            ps.setString(8, value.getIssuedDate());
            ps.setString(9, value.getStatus());
            ps.executeUpdate();
        } finally {
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /**
     * Inserts a payment only while the settlement still covers everything
     * issued on the claim. The balance test and the insert are a single
     * statement, so concurrent requests cannot both spend the same unpaid
     * balance. Returns false when the amount no longer fits.
     */
    public boolean insertWithinSettlement(Payment value) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement(
                    "insert into PAYMENT "
                    + "(payment_id,claim_id,settlement_id,payee_name,amount,"
                    + "payment_method,check_number,issued_date,status) "
                    + "select ?,?,?,?,?,?,?,?,? from (values(0)) "
                    + "where ? <= (select s.settlement_amount "
                    + "- coalesce((select sum(p.amount) from PAYMENT p "
                    + "where p.claim_id = s.claim_id),0) from SETTLEMENT s "
                    + "where s.settlement_id = ?)");
            ps.setInt(1, value.getPaymentId());
            ps.setInt(2, value.getClaimId());
            ps.setInt(3, value.getSettlementId());
            ps.setString(4, value.getPayeeName());
            ps.setDouble(5, value.getAmount());
            ps.setString(6, value.getPaymentMethod());
            ps.setString(7, value.getCheckNumber());
            ps.setString(8, value.getIssuedDate());
            ps.setString(9, value.getStatus());
            ps.setDouble(10, value.getAmount());
            ps.setInt(11, value.getSettlementId());
            return ps.executeUpdate() == 1;
        } finally {
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
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
