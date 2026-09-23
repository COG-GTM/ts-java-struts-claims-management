package com.northstar.settlement.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The two read-only lookups the settlement slice makes, issuing the same SQL as
 * ClaimDAO.findById (src/main/java/com/northstar/claims/dao/ClaimDAO.java:32)
 * and PolicyDAO.findById (src/main/java/com/northstar/claims/dao/PolicyDAO.java:32).
 *
 * <p>Implements SPEC-SETTLE-001 SETTLE-R07: the limit used by the calculation is
 * the {@code policy_limit} of the policy referenced by the claim's
 * {@code policy_id}; claim 119 references policy 9001 (limit 1000) and claim 120
 * references policy 9002 (limit 100000)
 * (src/main/resources/db/schema.sql:21; src/main/resources/db/seed.sql:49-50,253-254).
 * The calculate-path and save-path fallbacks around a missing row differ and
 * live in the controller (SETTLE-R08, SETTLE-R26).
 */
@Repository
public class ClaimPolicyRepository {

    private final JdbcTemplate jdbc;

    public ClaimPolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Empty when no CLAIM row exists, matching ClaimDAO.findById returning null. */
    public Optional<Integer> findPolicyId(int claimId) {
        return jdbc.query("select policy_id from CLAIM where claim_id = ?",
                rs -> rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty(),
                claimId);
    }

    /** Empty when no POLICY row exists, matching PolicyDAO.findById returning null. */
    public Optional<Double> findPolicyLimit(int policyId) {
        return jdbc.query("select policy_limit from POLICY where policy_id = ?",
                rs -> rs.next() ? Optional.of(rs.getDouble(1)) : Optional.empty(),
                policyId);
    }
}
