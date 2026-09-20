package com.northstar.settlement.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The {@code PolicyDAO.findById} lookup the settlement screens use for the cap (SETTLE-R47). */
@Repository
public class PolicyRepository {

    private final JdbcTemplate jdbc;

    public PolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Policy limit, or {@code null} when the policy does not exist (SETTLE-R48, SETTLE-R49). */
    public Double findPolicyLimit(int policyId) {
        List<Double> rows = jdbc.queryForList(
                "select policy_limit from policy where policy_id = ?", Double.class, policyId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
