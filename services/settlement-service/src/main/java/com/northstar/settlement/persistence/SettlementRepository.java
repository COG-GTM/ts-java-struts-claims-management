package com.northstar.settlement.persistence;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The SETTLEMENT table access of the slice, issuing the same SQL as
 * SettlementDAO and ClaimsActionSupport.nextId.
 *
 * <p>Implements SPEC-SETTLE-001:
 * <ul>
 *   <li>SETTLE-R27 — {@code settlement_id} is {@code max(settlement_id) + 1}
 *       (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75).</li>
 *   <li>SETTLE-R28 — the insert writes all nine SETTLEMENT columns
 *       (src/main/java/com/northstar/claims/dao/SettlementDAO.java:118-137).</li>
 *   <li>SETTLE-R30 — detail loads
 *       {@code select * from SETTLEMENT where claim_id = ? order by settlement_id desc}
 *       and takes the first row, or nothing
 *       (src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-114).</li>
 * </ul>
 */
@Repository
public class SettlementRepository {

    private final JdbcTemplate jdbc;

    public SettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** SETTLE-R27: the next key, allocated with no locking, as the legacy does. */
    public int nextId() {
        Integer id = jdbc.queryForObject(
                "select coalesce(max(settlement_id),0)+1 from SETTLEMENT",
                Integer.class);
        return id == null ? 1 : id;
    }

    /** SETTLE-R28: one insert of the nine columns. */
    public void save(StoredSettlement value) {
        jdbc.update("insert into SETTLEMENT "
                + "(settlement_id,claim_id,covered_amount,deductible_applied,"
                + "depreciation,capped_at_limit,settlement_amount,"
                + "calculated_by,calculated_date) values (?,?,?,?,?,?,?,?,?)",
                value.settlementId(), value.claimId(), value.coveredAmount(),
                value.deductibleApplied(), value.depreciation(),
                value.cappedAtLimit(), value.settlementAmount(),
                value.calculatedBy(), value.calculatedDate());
    }

    /** SETTLE-R30: the highest settlement_id for the claim, or empty. */
    public Optional<StoredSettlement> findLatestByClaim(int claimId) {
        return jdbc.query(
                "select * from SETTLEMENT where claim_id = ? order by settlement_id desc",
                rs -> rs.next() ? Optional.of(read(rs)) : Optional.empty(),
                claimId);
    }

    private StoredSettlement read(ResultSet rs) throws SQLException {
        Date calculatedDate = rs.getDate("calculated_date");
        return new StoredSettlement(
                rs.getInt("settlement_id"),
                rs.getInt("claim_id"),
                rs.getDouble("covered_amount"),
                rs.getDouble("deductible_applied"),
                rs.getDouble("depreciation"),
                rs.getBoolean("capped_at_limit"),
                rs.getDouble("settlement_amount"),
                rs.getString("calculated_by"),
                calculatedDate == null ? null : calculatedDate.toLocalDate().toString());
    }
}
