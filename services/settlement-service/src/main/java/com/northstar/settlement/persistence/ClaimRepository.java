package com.northstar.settlement.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The single claim lookup the settlement screens need: the claim's policy (SETTLE-R47). */
@Repository
public class ClaimRepository {

    private final JdbcTemplate jdbc;

    public ClaimRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Policy id of a claim, or {@code null} when the claim does not exist (SETTLE-R48, SETTLE-R49). */
    public Integer findPolicyId(int claimId) {
        List<Integer> rows = jdbc.queryForList(
                "select policy_id from claim where claim_id = ?", Integer.class, claimId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
