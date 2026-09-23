package com.northstar.settlement.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoint tests for GET /settlement/detail against the seeded fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:settlement-detail-test")
class SettlementDetailEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Rules: SETTLE-R30, SETTLE-R31.
     * Transcript: none — the fields are read from
     * src/main/webapp/WEB-INF/jsp/settlement/detail.jsp:10-26 and the row from
     * src/main/resources/db/seed.sql:613 (settlement 119 of claim 119: covered
     * 1500, deductible 500, depreciation 100, not capped, amount 0, adjuster1,
     * 2019-03-01).
     */
    @Test
    void detailShowsTheSeededSettlementForClaim119() throws Exception {
        mvc.perform(get("/settlement/detail").param("claimId", "119"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("detail"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.detailSettlementId").value("119"))
                .andExpect(jsonPath("$.fields.detailClaimId").value("119"))
                .andExpect(jsonPath("$.fields.detailCoveredAmount").value("1500.00"))
                .andExpect(jsonPath("$.fields.detailDeductible").value("500.00"))
                .andExpect(jsonPath("$.fields.detailDepreciation").value("100.00"))
                .andExpect(jsonPath("$.fields.detailCapped").value("false"))
                .andExpect(jsonPath("$.fields.detailAmount").value("0.00"))
                .andExpect(jsonPath("$.fields.detailDate").value("2019-03-01"));
    }

    /**
     * Rules: SETTLE-R02, SETTLE-R30.
     * Transcript: none — read from
     * src/main/java/com/northstar/claims/web/SettlementDetailAction.java:17-18,
     * where a missing claimId defaults to 119 through the same coercion as the
     * calculate path.
     */
    @Test
    void detailWithoutAClaimIdDefaultsTo119() throws Exception {
        mvc.perform(get("/settlement/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.detailClaimId").value("119"))
                .andExpect(jsonPath("$.fields.detailSettlementId").value("119"));
    }

    /**
     * Rules: SETTLE-R30.
     * Transcript: none — read from
     * src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-114, whose
     * {@code order by settlement_id desc} takes the highest id for the claim.
     * Claim 118 is seeded with settlement 118
     * (src/main/resources/db/seed.sql:612), so the inserted id 900 wins.
     */
    @Test
    void detailTakesTheHighestSettlementIdForTheClaim() throws Exception {
        jdbc.update("insert into SETTLEMENT "
                + "(settlement_id,claim_id,covered_amount,deductible_applied,"
                + "depreciation,capped_at_limit,settlement_amount,calculated_by,"
                + "calculated_date) values (?,?,?,?,?,?,?,?,?)",
                900, 118, 2500.0, 100.0, 0.0, true, 1000.0, "supervisor", "2019-04-01");

        mvc.perform(get("/settlement/detail").param("claimId", "118"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.detailSettlementId").value("900"))
                .andExpect(jsonPath("$.fields.detailCapped").value("true"))
                .andExpect(jsonPath("$.fields.detailAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.detailDate").value("2019-04-01"));
    }

    /**
     * Rules: SETTLE-R19, SETTLE-R30.
     * Transcript: none — read from
     * src/main/java/com/northstar/claims/web/SettlementDetailAction.java:19-20
     * and src/main/java/com/northstar/claims/web/tag/FieldTag.java:79-85: a
     * claim with no settlement row leaves the {@code settlement} attribute null
     * and every field renders as the empty string. The seeded SETTLEMENT rows
     * end at claim 120 (src/main/resources/db/seed.sql:613-614), so claim 9999
     * has none.
     */
    @Test
    void detailForAClaimWithNoSettlementIsEmpty() throws Exception {
        mvc.perform(get("/settlement/detail").param("claimId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("detail"))
                .andExpect(jsonPath("$.fields.detailSettlementId").value(""))
                .andExpect(jsonPath("$.fields.detailAmount").value(""))
                .andExpect(jsonPath("$.fields.detailDate").value(""));
    }
}
