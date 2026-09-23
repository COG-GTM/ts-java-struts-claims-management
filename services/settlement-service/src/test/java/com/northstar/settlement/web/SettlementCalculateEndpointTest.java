package com.northstar.settlement.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoint tests for POST and GET /settlement/calculate against the seeded fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:settlement-calculate-test")
class SettlementCalculateEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Rules: SETTLE-R01, SETTLE-R07, SETTLE-R09, SETTLE-R11, SETTLE-R13,
     * SETTLE-R20, SETTLE-R22, SETTLE-R23.
     * Transcript: settlement_calculate.
     */
    @Test
    void standardCalculationForClaim119() throws Exception {
        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "119")
                        .param("coveredAmount", "5000.00")
                        .param("deductible", "500.00")
                        .param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("calculate"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.coveredAmount").value("5000.00"))
                .andExpect(jsonPath("$.fields.deductibleApplied").value("500.00"))
                .andExpect(jsonPath("$.fields.depreciation").value("0.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("true"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("1000.00"));
    }

    /**
     * Rules: SETTLE-R01, SETTLE-R13.
     * Transcript: settlement_policy_cap.
     */
    @Test
    void policyCapForClaim119() throws Exception {
        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "119")
                        .param("coveredAmount", "20000.00")
                        .param("deductible", "100.00")
                        .param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.coveredAmount").value("20000.00"))
                .andExpect(jsonPath("$.fields.deductibleApplied").value("100.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("true"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("1000.00"));
    }

    /**
     * Rules: SETTLE-R05, SETTLE-R07, SETTLE-R10.
     * Transcript: settlement_blank_deductible.
     */
    @Test
    void blankDeductibleForClaim120() throws Exception {
        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "120")
                        .param("coveredAmount", "5000.00")
                        .param("deductible", "")
                        .param("depreciation", "500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.deductibleApplied").value("0.00"))
                .andExpect(jsonPath("$.fields.depreciation").value("500.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("false"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("4500.00"));
    }

    /**
     * Rules: SETTLE-R12.
     * Transcript: settlement_deductible_floor.
     */
    @Test
    void deductibleFloorForClaim120() throws Exception {
        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "120")
                        .param("coveredAmount", "1000.00")
                        .param("deductible", "2000.00")
                        .param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("false"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("0.00"));
    }

    /**
     * Rules: SETTLE-R15, SETTLE-R16, SETTLE-R20, SETTLE-R21.
     * Transcript: settlement_half_cent.
     */
    @Test
    void halfCentForClaim120() throws Exception {
        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "120")
                        .param("coveredAmount", "1.005")
                        .param("deductible", "")
                        .param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.coveredAmount").value("1.01"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("1.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("false"));
    }

    /**
     * Rules: SETTLE-R06.
     * Transcript: settlement_bad_deductible — the legacy forwards to error.jsp
     * with no business fields; per ADR-001 parity is on the status class, so
     * the service answers 500 with the same empty field set.
     */
    @Test
    void nonNumericDeductibleReachesTheErrorScreen() throws Exception {
        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "119")
                        .param("coveredAmount", "5000.00")
                        .param("deductible", "abc")
                        .param("depreciation", "0.00"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    /**
     * Rules: SETTLE-R08 (v2).
     * Transcript: none — SPEC-SETTLE-001 v0.2 records this as a live run against
     * the Struts app with the seeded database: claimId 9999 with covered 100 and
     * deductible 10 returns 90.00 uncapped, and covered 20000 returns 10000.00
     * capped, which exercises the 10000 fallback itself.
     */
    @Test
    void missingClaimFallsBackToTheLimit10000() throws Exception {
        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "9999")
                        .param("coveredAmount", "100")
                        .param("deductible", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.settlementAmount").value("90.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("false"));

        mvc.perform(post("/settlement/calculate")
                        .param("claimId", "9999")
                        .param("coveredAmount", "20000")
                        .param("deductible", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.settlementAmount").value("10000.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("true"));
    }

    /**
     * Rules: SETTLE-R01, SETTLE-R02, SETTLE-R03, SETTLE-R04, SETTLE-R05.
     * Transcript: none — the analysis records the same observation for the
     * monolith (docs/analysis/settlement-slice.md, item 11): with no amounts the
     * claim 119 screen still renders covered 5000.00, deductible 0.00, amount
     * 1000.00, capped true.
     */
    @Test
    void getWithNoParametersUsesEveryFallback() throws Exception {
        mvc.perform(get("/settlement/calculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("calculate"))
                .andExpect(jsonPath("$.fields.coveredAmount").value("5000.00"))
                .andExpect(jsonPath("$.fields.deductibleApplied").value("0.00"))
                .andExpect(jsonPath("$.fields.depreciation").value("0.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("true"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("1000.00"));
    }
}
