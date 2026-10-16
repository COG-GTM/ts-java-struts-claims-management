package com.northstar.payment.persistence;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The one SETTLEMENT read the payment slice makes: the claim's newest
 * settlement, {@code order by settlement_id desc} and first row
 * (src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-115), PAY-R05.
 * SETTLEMENT is never written by this service.
 */
@Repository
public class SettlementLookupRepository {

    /** The two settlement columns PaymentIssueAction.java:28,31 reads. */
    public record ClaimSettlement(int settlementId, double settlementAmount) {
    }

    private final JdbcTemplate jdbc;

    public SettlementLookupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** PAY-R05: the highest settlement_id for the claim, or empty when the claim has none. */
    public Optional<ClaimSettlement> findLatestByClaim(int claimId) {
        return jdbc.query(
                "select * from SETTLEMENT where claim_id = ? order by settlement_id desc",
                rs -> rs.next()
                        ? Optional.of(new ClaimSettlement(rs.getInt("settlement_id"),
                                rs.getDouble("settlement_amount")))
                        : Optional.empty(),
                claimId);
    }
}
