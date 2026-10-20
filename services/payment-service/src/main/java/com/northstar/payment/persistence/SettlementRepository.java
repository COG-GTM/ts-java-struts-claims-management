package com.northstar.payment.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The one SETTLEMENT read of the payment slice: SettlementDAO.findByClaim,
 * {@code select * from SETTLEMENT where claim_id = ? order by settlement_id desc},
 * first row or nothing (src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-115).
 */
@Repository
public class SettlementRepository {
    private final JdbcTemplate jdbc;

    public SettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Empty when the claim has no SETTLEMENT row or does not exist. */
    public Optional<NewestSettlement> findNewestByClaim(int claimId) {
        List<NewestSettlement> rows = jdbc.query(
                "select settlement_id, settlement_amount from SETTLEMENT"
                        + " where claim_id = ? order by settlement_id desc",
                (rs, i) -> new NewestSettlement(rs.getInt("settlement_id"),
                        rs.getDouble("settlement_amount")),
                claimId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record NewestSettlement(int settlementId, double settlementAmount) {
    }
}
