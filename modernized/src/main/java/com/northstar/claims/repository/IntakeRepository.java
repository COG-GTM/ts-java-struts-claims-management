package com.northstar.claims.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Holds the inline insert from IntakeSubmitAction without changing shared DAOs. */
@Repository
public class IntakeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public IntakeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(int claimId, String claimant, String lossDate, String description) {
        // The legacy action ignores submitted policy/lossType/assignment values.
        jdbc.getJdbcOperations().update("insert into CLAIM values "
                + "(?,?,9001,?,?,?,?,?,'OPEN',0,'adjuster1','supervisor',?)",
                claimId, "CLM-" + claimId, claimant, lossDate, lossDate,
                "WATER", description, lossDate);
    }
}
