package com.northstar.settlement.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Replaces {@code ClaimDAO.findById} as reached through
 * {@code ClaimsActionSupport.findClaim} (SETTLE-R14, SETTLE-R15).
 */
@Repository
public class ClaimRepository {

    private final JdbcClient jdbc;

    public ClaimRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ClaimRecord> findById(int claimId) {
        return jdbc.sql("select claim_id, policy_id, status from claim where claim_id = :id")
                .param("id", claimId)
                .query((rs, rowNum) -> new ClaimRecord(
                        rs.getInt("claim_id"), rs.getInt("policy_id"), rs.getString("status")))
                .optional();
    }
}
