package com.northstar.claims.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.domain.Policy;


/**
 * JDBC gateway for POLICY records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
@org.springframework.stereotype.Repository
public class PolicyDAO {

    private final org.springframework.jdbc.core.JdbcOperations jdbc;

    public PolicyDAO(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        this.jdbc = template.getJdbcOperations();
    }

    private static final Log log = LogFactory.getLog(PolicyDAO.class);

    /** Loads one row by its numeric primary key. */
    public Policy findById(int id) throws SQLException {
        return jdbc.query("select * from POLICY where policy_id = ?", ps -> {
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
        return jdbc.query("select * from POLICY order by policy_id", rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Counts rows for summary pages. */
    public int count() throws SQLException {
        return jdbc.query("select count(*) from POLICY", rs -> {
            return rs.next() ? rs.getInt(1) : 0;
        });
    }

    /** Deletes a row when an administrator removes a record. */
    public void delete(int id) throws SQLException {
        jdbc.update("delete from POLICY where policy_id = ?", ps -> {
            ps.setInt(1, id);
        });
    }

    /** Searches policies using the historical concatenated filter style. */
    public List findByLine(String lineOfBusiness) throws SQLException {
        List rows = new ArrayList();
            StringBuffer sql = new StringBuffer();
            sql.append("select * from POLICY where line_of_business = '");
            sql.append(lineOfBusiness);
            sql.append("' order by policy_number");
        return jdbc.query(sql.toString(), rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Returns policies whose number or insured name matches a search term. */
    public List search(String term) throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select * from POLICY where policy_number like ? or insured_name like ?", ps -> {
            ps.setString(1, "%" + term + "%");
            ps.setString(2, "%" + term + "%");
        }, rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Updates the policy status used by underwriting screens. */
    public void updateStatus(int id, String status) throws SQLException {
        jdbc.update("update POLICY set status = ? where policy_id = ?", ps -> {
            ps.setString(1, status);
            ps.setInt(2, id);
        });
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
