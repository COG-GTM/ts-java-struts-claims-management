package com.northstar.settlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoint tests for POST /settlement/save against the seeded fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:settlement-save-test")
class SettlementSaveEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Rules: SETTLE-R25, SETTLE-R27, SETTLE-R28, SETTLE-R29.
     * Transcript: settlement_save — savedBy supervisor, settlementAmount
     * 1000.00, and db_state settlement.claim.119.amount 1000.00. The new key is
     * max(settlement_id)+1
     * (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75).
     */
    @Test
    void saveRecomputesAndInsertsOneRowForClaim119() throws Exception {
        int expectedId = jdbc.queryForObject(
                "select coalesce(max(settlement_id),0)+1 from SETTLEMENT", Integer.class);

        mvc.perform(post("/settlement/save")
                        .param("claimId", "119")
                        .param("coveredAmount", "5000.00")
                        .param("deductible", "500.00")
                        .param("depreciation", "0.00")
                        .param("user", "supervisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("save"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.settlementAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.savedBy").value("supervisor"));

        Map<String, Object> row = jdbc.queryForMap(
                "select * from SETTLEMENT where claim_id = 119 order by settlement_id desc limit 1");
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(expectedId);
        assertThat(((Number) row.get("COVERED_AMOUNT")).doubleValue()).isEqualTo(5000.00);
        assertThat(((Number) row.get("DEDUCTIBLE_APPLIED")).doubleValue()).isEqualTo(500.00);
        assertThat(((Number) row.get("DEPRECIATION")).doubleValue()).isEqualTo(0.00);
        assertThat(row.get("CAPPED_AT_LIMIT")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) row.get("SETTLEMENT_AMOUNT")).doubleValue()).isEqualTo(1000.00);
        assertThat(row.get("CALCULATED_BY")).isEqualTo("supervisor");
        assertThat(String.valueOf(row.get("CALCULATED_DATE"))).isEqualTo("2019-04-01");
    }

    /**
     * Rules: SETTLE-R17, SETTLE-R28.
     * Transcript: settlement_half_cent for the arithmetic; the persistence of
     * the unrounded inputs is read from
     * src/main/java/com/northstar/claims/dao/SettlementDAO.java:118-137, which
     * writes the calculator's own field values.
     */
    @Test
    void savedRowKeepsTheUnroundedCoveredAmount() throws Exception {
        mvc.perform(post("/settlement/save")
                        .param("claimId", "120")
                        .param("coveredAmount", "1.005")
                        .param("deductible", "")
                        .param("depreciation", "0.00")
                        .param("user", "supervisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.settlementAmount").value("1.00"));

        Map<String, Object> row = jdbc.queryForMap(
                "select * from SETTLEMENT where claim_id = 120 order by settlement_id desc limit 1");
        assertThat(((Number) row.get("COVERED_AMOUNT")).doubleValue()).isEqualTo(1.005);
        assertThat(((Number) row.get("SETTLEMENT_AMOUNT")).doubleValue()).isEqualTo(1.00);
    }

    /**
     * Rules: SETTLE-R26, SETTLE-R06.
     * Transcript: none — read from
     * src/main/java/com/northstar/claims/web/SettlementSaveAction.java:25-33,
     * where the claim and policy are dereferenced without a null check and
     * there is no 10000 fallback, so the request reaches error.jsp
     * (src/main/webapp/WEB-INF/struts-config.xml:59-60). Per ADR-001 parity is
     * on the status class, so the service answers 500.
     */
    @Test
    void saveAgainstAMissingClaimFails() throws Exception {
        mvc.perform(post("/settlement/save")
                        .param("claimId", "9999")
                        .param("coveredAmount", "100")
                        .param("deductible", "10")
                        .param("user", "supervisor"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty());

        assertThat(jdbc.queryForObject(
                "select count(*) from SETTLEMENT where claim_id = 9999", Integer.class))
                .isZero();
    }

    /**
     * Rules: SETTLE-R06, SETTLE-R24.
     * Transcript: settlement_bad_deductible for the error screen; the absence
     * of a write follows from the calculator throwing before
     * SettlementSaveAction.java:34-39 reaches the insert.
     */
    @Test
    void nonNumericDeductibleWritesNothing() throws Exception {
        int before = jdbc.queryForObject("select count(*) from SETTLEMENT", Integer.class);

        mvc.perform(post("/settlement/save")
                        .param("claimId", "119")
                        .param("coveredAmount", "5000.00")
                        .param("deductible", "abc")
                        .param("depreciation", "0.00")
                        .param("user", "supervisor"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.screen").value("error"));

        assertThat(jdbc.queryForObject("select count(*) from SETTLEMENT", Integer.class))
                .isEqualTo(before);
    }
}
