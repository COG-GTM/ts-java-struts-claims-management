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
import com.northstar.claims.model.Adjuster;


/**
 * JDBC gateway for ADJUSTER records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
public class AdjusterDAO {

    private static final Log log = LogFactory.getLog(AdjusterDAO.class);

    /** Loads one row by its numeric primary key. */
    public Adjuster findById(int id) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select * from ADJUSTER where adjuster_id = ?");
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
            rs = st.executeQuery("select * from ADJUSTER order by adjuster_id");
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
            rs = st.executeQuery("select count(*) from ADJUSTER");
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
            ps = conn.prepareStatement("delete from ADJUSTER where adjuster_id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
        } finally {
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Authenticates a user using the plaintext credential columns of the application. */
    public Adjuster authenticate(String username, String password) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select * from ADJUSTER where username = ? and password = ? and active = true");
            ps.setString(1, username);
            ps.setString(2, password);
            rs = ps.executeQuery();
            return rs.next() ? read(rs) : null;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Loads one row by its login name. */
    public Adjuster findByUsername(String username) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            ps = conn.prepareStatement("select * from ADJUSTER where username = ?");
            ps.setString(1, username);
            rs = ps.executeQuery();
            return rs.next() ? read(rs) : null;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { ps.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Returns all active adjusters for assignment lists. */
    public List findActive() throws SQLException {
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            st = conn.createStatement();
            rs = st.executeQuery("select * from ADJUSTER where active = true order by full_name");
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

    /** Converts a result set row into the corresponding mutable bean. */
    private Adjuster read(ResultSet rs) throws SQLException {
        Adjuster value = new Adjuster();
        value.setAdjusterId(rs.getInt("adjuster_id"));
        value.setUsername(rs.getString("username"));
        value.setPassword(rs.getString("password"));
        value.setFullName(rs.getString("full_name"));
        value.setRegion(rs.getString("region"));
        value.setActive(rs.getBoolean("active"));
        return value;
    }

    /** Logs the statement boundary used by old production diagnostics. */
    private void trace(String text) {
        log.debug(text);
        System.out.println(text);
    }
}
