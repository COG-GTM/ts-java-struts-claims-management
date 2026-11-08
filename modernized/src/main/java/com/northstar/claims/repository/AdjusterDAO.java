package com.northstar.claims.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.domain.Adjuster;


/**
 * JDBC gateway for ADJUSTER records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
@org.springframework.stereotype.Repository
public class AdjusterDAO {

    private final org.springframework.jdbc.core.JdbcOperations jdbc;

    public AdjusterDAO(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        this.jdbc = template.getJdbcOperations();
    }

    private static final Log log = LogFactory.getLog(AdjusterDAO.class);

    /** Loads one row by its numeric primary key. */
    public Adjuster findById(int id) throws SQLException {
        return jdbc.query("select * from ADJUSTER where adjuster_id = ?", ps -> {
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
        return jdbc.query("select * from ADJUSTER order by adjuster_id", rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Counts rows for summary pages. */
    public int count() throws SQLException {
        return jdbc.query("select count(*) from ADJUSTER", rs -> {
            return rs.next() ? rs.getInt(1) : 0;
        });
    }

    /** Deletes a row when an administrator removes a record. */
    public void delete(int id) throws SQLException {
        jdbc.update("delete from ADJUSTER where adjuster_id = ?", ps -> {
            ps.setInt(1, id);
        });
    }

    /** Authenticates a user using the plaintext credential columns of the application. */
    public Adjuster authenticate(String username, String password) throws SQLException {
        return jdbc.query("select * from ADJUSTER where username = ? and password = ? and active = true", ps -> {
            ps.setString(1, username);
            ps.setString(2, password);
        }, rs -> {
            return rs.next() ? read(rs) : null;
        });
    }

    /** Returns all active adjusters for assignment lists. */
    public List findActive() throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select * from ADJUSTER where active = true order by full_name", rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
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
