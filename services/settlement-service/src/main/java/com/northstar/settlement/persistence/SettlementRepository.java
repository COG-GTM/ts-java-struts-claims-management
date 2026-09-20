package com.northstar.settlement.persistence;

import com.northstar.settlement.calc.Settlement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** The {@code SETTLEMENT} statements of the legacy {@code SettlementDAO}, on PostgreSQL. */
@Repository
public class SettlementRepository {

    private static final RowMapper<Settlement> MAPPER = (rs, rowNum) -> new Settlement(
            rs.getInt("settlement_id"),
            rs.getInt("claim_id"),
            rs.getDouble("covered_amount"),
            rs.getDouble("deductible_applied"),
            rs.getDouble("depreciation"),
            rs.getBoolean("capped_at_limit"),
            rs.getDouble("settlement_amount"),
            rs.getString("calculated_by"),
            rs.getString("calculated_date"));

    private final JdbcTemplate jdbc;

    public SettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The highest-id settlement of a claim, which is what the detail screen shows
     * (SETTLE-R03, SETTLE-R45); {@code null} when the claim has none (SETTLE-R46).
     */
    public Settlement findLatestByClaim(int claimId) {
        List<Settlement> rows = jdbc.query(
                "select * from settlement where claim_id = ? order by settlement_id desc limit 1",
                MAPPER, claimId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code max(settlement_id) + 1} over the whole table, without locking (SETTLE-R39). */
    public int nextId() {
        Integer next = jdbc.queryForObject("select coalesce(max(settlement_id),0)+1 from settlement", Integer.class);
        return next == null ? 1 : next;
    }

    /** Inserts a new row; settlements are never updated or deleted by these screens (SETTLE-R38, SETTLE-R42). */
    public void insert(Settlement value) {
        jdbc.update("insert into settlement (settlement_id,claim_id,covered_amount,deductible_applied,"
                + "depreciation,capped_at_limit,settlement_amount,calculated_by,calculated_date) "
                + "values (?,?,?,?,?,?,?,?,cast(? as date))",
                value.settlementId(), value.claimId(), value.coveredAmount(), value.deductibleApplied(),
                value.depreciation(), value.cappedAtLimit(), value.settlementAmount(),
                value.calculatedBy(), value.calculatedDate());
    }
}
