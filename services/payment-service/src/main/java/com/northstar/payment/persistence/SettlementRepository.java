package com.northstar.payment.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The one read of SETTLEMENT the payment slice makes: SettlementDAO.findByClaim,
 * the newest row for the claim by descending settlement_id
 * (src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-114).
 *
 * <p>Implements SPEC-PAY-001 PAY-R03 (the payment links to the latest
 * settlement) and PAY-R04 (its settlement_amount is the amount fallback)
 * (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,28,30-31).
 */
@Repository
public class SettlementRepository {
    private final JdbcTemplate jdbc;

    public SettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** SettlementDAO.java:100-114; empty when the claim has no settlement (PAY-R11). */
    public Optional<LatestSettlement> findLatestByClaim(int claimId) {
        List<LatestSettlement> rows = jdbc.query(
                "select settlement_id, settlement_amount from SETTLEMENT"
                        + " where claim_id = ? order by settlement_id desc",
                (rs, i) -> new LatestSettlement(rs.getInt("settlement_id"),
                        rs.getDouble("settlement_amount")),
                claimId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record LatestSettlement(int settlementId, double settlementAmount) {
    }
}
