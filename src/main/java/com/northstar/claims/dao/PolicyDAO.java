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
import com.northstar.claims.model.Policy;


/**
 * JDBC gateway for POLICY records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
public class PolicyDAO {

    private static final Log log = LogFactory.getLog(PolicyDAO.class);

    /** Loads one row by its numeric primary key. */
    public Policy findById(int id) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select * from POLICY where policy_id = ?");
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
            rs = st.executeQuery("select * from POLICY order by policy_id");
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
            rs = st.executeQuery("select count(*) from POLICY");
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
            ps = conn.prepareStatement("delete from POLICY where policy_id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
        } finally {
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Searches policies filtered by line of business. */
    public List findByLine(String lineOfBusiness) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement(
                    "select * from POLICY where line_of_business = ? order by policy_number");
            ps.setString(1, lineOfBusiness);
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

    /** Returns policies whose number or insured name matches a search term. */
    public List search(String term) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select * from POLICY where policy_number like ? or insured_name like ?");
            ps.setString(1, "%" + term + "%");
            ps.setString(2, "%" + term + "%");
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

    /** Updates the policy status used by underwriting screens. */
    public void updateStatus(int id, String status) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("update POLICY set status = ? where policy_id = ?");
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        } finally {
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Converts a result set row into the corresponding mutable bean. */
    private Policy read(ResultSet rs) throws SQLException {
        Policy value = new Policy();
        value.setPolicyId(rs.getInt("policy_id"));
        value.setPolicyNumber(rs.getString("policy_number"));
        value.setLineOfBusiness(rs.getString("line_of_business"));
        value.setInsuredName(rs.getString("insured_name"));
        value.setInsuredAddress(rs.getString("insured_address"));
        value.setEffectiveDate(rs.getString("effective_date"));
        value.setExpiryDate(rs.getString("expiry_date"));
        value.setPolicyLimit(rs.getDouble("policy_limit"));
        value.setDeductible(rs.getDouble("deductible"));
        value.setAnnualPremium(rs.getDouble("annual_premium"));
        value.setStatus(rs.getString("status"));
        return value;
    }

    /** Logs the statement boundary used by old production diagnostics. */
    private void trace(String text) {
        log.debug(text);
        System.out.println(text);
    }
}
