package com.northstar.settlement.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Replaces ClaimDAO.findById for the one column the slice needs. */
@Repository
public class ClaimRepository {

    private final JdbcTemplate jdbc;

    public ClaimRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> findPolicyId(int claimId) {
        return jdbc.query("select policy_id from CLAIM where claim_id = ?",
                (rs, i) -> rs.getInt("policy_id"), claimId).stream().findFirst();
    }
}
