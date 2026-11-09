package com.northstar.claims.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.domain.Claim;


/**
 * JDBC gateway for CLAIM records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
@org.springframework.stereotype.Repository
public class ClaimDAO {

    private final org.springframework.jdbc.core.JdbcOperations jdbc;

    public ClaimDAO(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        this.jdbc = template.getJdbcOperations();
    }

    private static final Log log = LogFactory.getLog(ClaimDAO.class);

    /** Loads one row by its numeric primary key. */
    public Claim findById(int id) throws SQLException {
        return jdbc.query("select * from CLAIM where claim_id = ?", ps -> {
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
        return jdbc.query("select * from CLAIM order by claim_id", rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Counts rows for summary pages. */
    public int count() throws SQLException {
        return jdbc.query("select count(*) from CLAIM", rs -> {
            return rs.next() ? rs.getInt(1) : 0;
        });
    }

    /** Deletes a row when an administrator removes a record. */
    public void delete(int id) throws SQLException {
        jdbc.update("delete from CLAIM where claim_id = ?", ps -> {
            ps.setInt(1, id);
        });
    }

    /** Finds claims in a workflow status. */
    public List findByStatus(String status) throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select * from CLAIM where status = ? order by claim_id", ps -> {
            ps.setString(1, status);
        }, rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Searches descriptions and statuses using the old concatenated SQL path. */
    public List search(String term, String status) throws SQLException {
        List rows = new ArrayList();
            StringBuffer sql = new StringBuffer("select * from CLAIM where description like '%");
            sql.append(term);
            sql.append("%'");
            if (status != null && status.length() > 0) {
                sql.append(" and status = '");
                sql.append(status);
                sql.append("'");
            }
        return jdbc.query(sql.toString(), rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Changes the adjuster assigned to a claim. */
    public void assign(int claimId, String adjuster) throws SQLException {
        updateText(claimId, "assigned_adjuster", adjuster);
    }

    /** Changes the claim status after workflow review. */
    public void updateStatus(int claimId, String status) throws SQLException {
        updateText(claimId, "status", status);
    }

    /** Changes the reserve amount and preserves the old DAO signature style. */
    public void updateReserve(int claimId, double reserve) throws SQLException {
        jdbc.update("update CLAIM set reserve_amount = ? where claim_id = ?", ps -> {
            ps.setDouble(1, reserve);
            ps.setInt(2, claimId);
        });
    }

    /** Inserts a claim submitted through FNOL. */
    public void insert(Claim value) throws SQLException {
        jdbc.update("insert into CLAIM (claim_id,claim_number,policy_id,claimant_name,status,reserve_amount) values (?,?,?,?,?,?)", ps -> {
            ps.setInt(1, value.getClaimId());
            ps.setString(2, value.getClaimNumber());
            ps.setInt(3, value.getPolicyId());
            ps.setString(4, value.getClaimantName());
            ps.setString(5, value.getStatus());
            ps.setDouble(6, value.getReserveAmount());
        });
    }

    /** Updates a text column used by the assignment and status actions. */
    private void updateText(int claimId, String column, String value) throws SQLException {
        jdbc.update("update CLAIM set " + column + " = ? where claim_id = ?", ps -> {
            ps.setString(1, value);
            ps.setInt(2, claimId);
        });
    }

    /** Converts a result set row into the corresponding mutable bean. */
    private Claim read(ResultSet rs) throws SQLException {
        Claim value = new Claim();
        value.setClaimId(rs.getInt("claim_id"));
        value.setClaimNumber(rs.getString("claim_number"));
        value.setPolicyId(rs.getInt("policy_id"));
        value.setClaimantName(rs.getString("claimant_name"));
        value.setLossDate(rs.getString("loss_date"));
        value.setReportedDate(rs.getString("reported_date"));
        value.setLossType(rs.getString("loss_type"));
        value.setDescription(rs.getString("description"));
        value.setStatus(rs.getString("status"));
        value.setReserveAmount(rs.getDouble("reserve_amount"));
        value.setAssignedAdjuster(rs.getString("assigned_adjuster"));
        value.setCreatedBy(rs.getString("created_by"));
        value.setCreatedDate(rs.getString("created_date"));
        return value;
    }

    /** Logs the statement boundary used by old production diagnostics. */
    private void trace(String text) {
        log.debug(text);
        System.out.println(text);
    }
}
