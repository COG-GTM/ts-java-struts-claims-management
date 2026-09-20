package com.northstar.settlement.persistence;

import java.sql.Date;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Replaces SettlementDAO and ClaimsActionSupport.nextId("SETTLEMENT"). */
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

    public SettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Allocates {@code max(settlement_id) + 1} and inserts under one lock, so
     * two saves cannot draw the same identifier. The legacy schema has no
     * sequence or identity column and this service is the only writer.
     */
    public SettlementRow insertNext(int claimId, double coveredAmount,
            double deductibleApplied, double depreciation,
            boolean cappedAtLimit, double settlementAmount,
            String calculatedBy, String calculatedDate) {
        synchronized (this) {
            Integer max = jdbc.queryForObject(
                    "select max(settlement_id) from SETTLEMENT", Integer.class);
            SettlementRow row = new SettlementRow(max == null ? 1 : max + 1,
                    claimId, coveredAmount, deductibleApplied, depreciation,
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
        }
    }

    public Optional<SettlementRow> findLatest(int claimId) {
        return jdbc.query("select * from SETTLEMENT where claim_id = ? "
                + "order by settlement_id desc", ROW, claimId)
                .stream().findFirst();
    }
}
