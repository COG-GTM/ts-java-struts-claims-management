package com.northstar.settlement.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SettlementControllerTest {

    @Autowired
    private MockMvc mvc;

    /** SETTLE-R01, R05, R07, R08, R10, R11: transcripts/settlement_calculate.json. */
    @Test
    void calculateMatchesTranscript() throws Exception {
        mvc.perform(post("/settlement/calculate")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("claimId", "119").param("coveredAmount", "5000.00")
                .param("deductible", "500.00").param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("settlement/calculate"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("true"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    /** SETTLE-R05 v2: no such claim, limit falls back to 10000. */
    @Test
    void unknownClaimUsesFallbackLimit() throws Exception {
        mvc.perform(post("/settlement/calculate")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("claimId", "9999").param("coveredAmount", "100")
                .param("deductible", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.settlementAmount").value("90.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("false"));
    }

    /** SETTLE-R06 v2: transcripts/settlement_bad_deductible.json. */
    @Test
    void nonNumericDeductibleIsSystemError() throws Exception {
        mvc.perform(post("/settlement/calculate")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("claimId", "119").param("coveredAmount", "5000.00")
                .param("deductible", "abc").param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    /** SETTLE-R12, SETTLE-R13: transcripts/settlement_save.json then detail. */
    @Test
    void saveThenDetailShowsSavedRow() throws Exception {
        mvc.perform(post("/settlement/save")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("claimId", "120").param("coveredAmount", "5000.00")
                .param("deductible", "500.00").param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("settlement/save"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("4500.00"))
                .andExpect(jsonPath("$.fields.savedBy").value("supervisor"));
        mvc.perform(get("/settlement/detail").param("claimId", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.detailAmount").value("4500.00"))
                .andExpect(jsonPath("$.fields.detailDate").value("2019-04-01"));
    }
}
