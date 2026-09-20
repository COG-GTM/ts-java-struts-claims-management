package com.northstar.settlement.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Replaces {@code PolicyDAO.findById(int)} for the single column the settlement
 * seam uses: {@code policy_limit} (SETTLE-R14).
 */
@Repository
public class PolicyRepository {

    private final JdbcClient jdbc;

    public PolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Double> findLimit(int policyId) {
        return jdbc.sql("select policy_limit from policy where policy_id = :id")
                .param("id", policyId)
                .query(Double.class)
                .optional();
    }
}
