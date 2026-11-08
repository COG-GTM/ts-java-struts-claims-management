package com.northstar.claims.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.domain.Settlement;


/**
 * JDBC gateway for SETTLEMENT records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
@org.springframework.stereotype.Repository
public class SettlementDAO {

    private final org.springframework.jdbc.core.JdbcOperations jdbc;

    public SettlementDAO(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        this.jdbc = template.getJdbcOperations();
    }

    private static final Log log = LogFactory.getLog(SettlementDAO.class);

    /** Loads one row by its numeric primary key. */
    public Settlement findById(int id) throws SQLException {
        return jdbc.query("select * from SETTLEMENT where settlement_id = ?", ps -> {
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
        return jdbc.query("select * from SETTLEMENT order by settlement_id", rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Counts rows for summary pages. */
    public int count() throws SQLException {
        return jdbc.query("select count(*) from SETTLEMENT", rs -> {
            return rs.next() ? rs.getInt(1) : 0;
        });
    }

    /** Deletes a row when an administrator removes a record. */
    public void delete(int id) throws SQLException {
        jdbc.update("delete from SETTLEMENT where settlement_id = ?", ps -> {
            ps.setInt(1, id);
        });
    }

    /** Loads the latest settlement for a claim. */
    public Settlement findByClaim(int claimId) throws SQLException {
        return jdbc.query("select * from SETTLEMENT where claim_id = ? order by settlement_id desc", ps -> {
            ps.setInt(1, claimId);
        }, rs -> {
            return rs.next() ? read(rs) : null;
        });
    }

    /** Persists the calculated settlement using a prepared statement. */
    public void save(Settlement value) throws SQLException {
        jdbc.update(
                    "insert into SETTLEMENT "
                    + "(settlement_id,claim_id,covered_amount,deductible_applied,"
                    + "depreciation,capped_at_limit,settlement_amount,"
                    + "calculated_by,calculated_date) values (?,?,?,?,?,?,?,?,?)", ps -> {
            ps.setInt(1, value.getSettlementId());
            ps.setInt(2, value.getClaimId());
            ps.setDouble(3, value.getCoveredAmount());
            ps.setDouble(4, value.getDeductibleApplied());
            ps.setDouble(5, value.getDepreciation());
            ps.setBoolean(6, value.isCappedAtLimit());
            ps.setDouble(7, value.getSettlementAmount());
            ps.setString(8, value.getCalculatedBy());
            ps.setString(9, value.getCalculatedDate());
        });
    }

    /** Converts a result set row into the corresponding mutable bean. */
    private Settlement read(ResultSet rs) throws SQLException {
        Settlement value = new Settlement();
        value.setSettlementId(rs.getInt("settlement_id"));
        value.setClaimId(rs.getInt("claim_id"));
        value.setCoveredAmount(rs.getDouble("covered_amount"));
        value.setDeductibleApplied(rs.getDouble("deductible_applied"));
        value.setDepreciation(rs.getDouble("depreciation"));
        value.setCappedAtLimit(rs.getBoolean("capped_at_limit"));
        value.setSettlementAmount(rs.getDouble("settlement_amount"));
        value.setCalculatedBy(rs.getString("calculated_by"));
        value.setCalculatedDate(rs.getString("calculated_date"));
        return value;
    }

    /** Logs the statement boundary used by old production diagnostics. */
    private void trace(String text) {
        log.debug(text);
        System.out.println(text);
    }
}
