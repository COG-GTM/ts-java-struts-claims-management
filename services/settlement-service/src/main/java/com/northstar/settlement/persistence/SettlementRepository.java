package com.northstar.settlement.persistence;

import java.sql.Date;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Replaces SettlementDAO and ClaimsActionSupport.nextId("SETTLEMENT").
 * SETTLEMENT has no sequence or identity column, so the id is
 * {@code max(settlement_id) + 1} (SETTLE-R12). This service is the only
 * writer, so allocation and insert share one process-wide lock.
 */
@Repository
public class SettlementRepository {

    private static final RowMapper<SettlementRow> ROW = (rs, i) ->
            new SettlementRow(rs.getInt("settlement_id"), rs.getInt("claim_id"),
                    rs.getDouble("covered_amount"),
                    rs.getDouble("deductible_applied"),
                    rs.getDouble("depreciation"),
                    rs.getBoolean("capped_at_limit"),
                    rs.getDouble("settlement_amount"),
                    rs.getString("calculated_by"),
                    rs.getDate("calculated_date").toString());

    private final JdbcTemplate jdbc;
    private final ReentrantLock allocation = new ReentrantLock();

    public SettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Allocates the next settlement_id and inserts the row under one lock. */
    public SettlementRow save(int claimId, double coveredAmount,
            double deductibleApplied, double depreciation, boolean cappedAtLimit,
            double settlementAmount, String calculatedBy, String calculatedDate) {
        allocation.lock();
        try {
            SettlementRow row = new SettlementRow(nextId(), claimId,
                    coveredAmount, deductibleApplied, depreciation,
                    cappedAtLimit, settlementAmount, calculatedBy,
                    calculatedDate);
            jdbc.update("insert into SETTLEMENT (settlement_id,claim_id,"
                    + "covered_amount,deductible_applied,depreciation,"
                    + "capped_at_limit,settlement_amount,calculated_by,"
                    + "calculated_date) values (?,?,?,?,?,?,?,?,?)",
                    row.settlementId(), row.claimId(), row.coveredAmount(),
                    row.deductibleApplied(), row.depreciation(),
                    row.cappedAtLimit(), row.settlementAmount(),
                    row.calculatedBy(), Date.valueOf(row.calculatedDate()));
            return row;
        } finally {
            allocation.unlock();
        }
    }

    private int nextId() {
        Integer max = jdbc.queryForObject(
                "select max(settlement_id) from SETTLEMENT", Integer.class);
        return max == null ? 1 : max + 1;
    }

    public Optional<SettlementRow> findLatest(int claimId) {
        return jdbc.query("select * from SETTLEMENT where claim_id = ? "
                + "order by settlement_id desc", ROW, claimId)
                .stream().findFirst();
    }
}
