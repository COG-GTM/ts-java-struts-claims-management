package com.northstar.settlement.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Replaces {@code SettlementDAO.findByClaim}/{@code save} and the
 * {@code ClaimsActionSupport.nextId("SETTLEMENT")} allocator. All statements are
 * parameterized; the legacy allocator concatenated the table name into SQL.
 */
@Repository
public class SettlementRepository {

    private final JdbcClient jdbc;

    public SettlementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * SETTLE-R39: legacy-faithful {@code max(settlement_id) + 1} over the whole table,
     * read in its own statement without locking, so concurrent saves can collide.
     */
    public int nextId() {
        return jdbc.sql("select coalesce(max(settlement_id), 0) + 1 from settlement")
                .query(Integer.class)
                .single();
    }

    /** SETTLE-R38: insert only; settlement rows are never updated or deleted. */
    public void insert(SettlementRow row) {
        jdbc.sql("insert into settlement (settlement_id, claim_id, covered_amount, deductible_applied,"
                        + " depreciation, capped_at_limit, settlement_amount, calculated_by, calculated_date)"
                        + " values (:settlementId, :claimId, :coveredAmount, :deductibleApplied,"
                        + " :depreciation, :cappedAtLimit, :settlementAmount, :calculatedBy, :calculatedDate)")
                .param("settlementId", row.settlementId())
                .param("claimId", row.claimId())
                .param("coveredAmount", row.coveredAmount())
                .param("deductibleApplied", row.deductibleApplied())
                .param("depreciation", row.depreciation())
                .param("cappedAtLimit", row.cappedAtLimit())
                .param("settlementAmount", row.settlementAmount())
                .param("calculatedBy", row.calculatedBy())
                .param("calculatedDate", row.calculatedDate())
                .update();
    }

    /** SETTLE-R45: the row with the highest settlement_id for the claim, not the newest by date. */
    public Optional<SettlementRow> findLatestByClaim(int claimId) {
        return jdbc.sql("select settlement_id, claim_id, covered_amount, deductible_applied, depreciation,"
                        + " capped_at_limit, settlement_amount, calculated_by, calculated_date"
                        + " from settlement where claim_id = :claimId order by settlement_id desc limit 1")
                .param("claimId", claimId)
                .query((rs, rowNum) -> new SettlementRow(
                        rs.getInt("settlement_id"),
                        rs.getInt("claim_id"),
                        rs.getDouble("covered_amount"),
                        rs.getDouble("deductible_applied"),
                        rs.getDouble("depreciation"),
                        rs.getBoolean("capped_at_limit"),
                        rs.getDouble("settlement_amount"),
                        rs.getString("calculated_by"),
                        rs.getObject("calculated_date", LocalDate.class)))
                .optional();
    }
}
