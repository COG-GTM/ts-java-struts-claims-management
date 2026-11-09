package com.northstar.claims.repository.workbench;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Keeps the action's inline SQL and its failure behavior separate from shared DAOs. */
@Repository
public class WorkbenchUpdates {
    private final NamedParameterJdbcTemplate jdbc;

    public WorkbenchUpdates(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void assign(int id, String adjuster) {
        jdbc.getJdbcOperations().update("update CLAIM set assigned_adjuster = '" + adjuster
                + "' where claim_id = " + id);
    }

    public void status(int id, String status) {
        jdbc.getJdbcOperations().update("update CLAIM set status = '" + status
                + "' where claim_id = " + id);
    }

    public void reserve(int id, double reserve) {
        jdbc.getJdbcOperations().update("update CLAIM set reserve_amount = " + reserve
                + " where claim_id = " + id);
    }
}
