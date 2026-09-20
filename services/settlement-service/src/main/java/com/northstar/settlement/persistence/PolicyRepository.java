package com.northstar.settlement.persistence;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Replaces PolicyDAO.findById for the one column the slice needs. */
@Repository
public class PolicyRepository {

    private final JdbcTemplate jdbc;

    public PolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BigDecimal> findLimit(int policyId) {
        return jdbc.query("select policy_limit from POLICY where policy_id = ?",
                (rs, i) -> BigDecimal.valueOf(rs.getDouble("policy_limit")),
                policyId).stream().findFirst();
    }
}
