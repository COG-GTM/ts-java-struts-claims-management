package com.northstar.settlement.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.settlement.persistence.SettlementRepository;
import com.northstar.settlement.persistence.StoredSettlement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoint tests for the global error mapping of the settlement screens. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:settlement-error-test")
class SettlementErrorHandlerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SettlementRepository settlements;

    /**
     * Rules: none — this covers the global exception mapping itself, not a
     * settlement rule.
     * Transcript: settlement_bad_deductible for the shape of the error screen
     * (no business fields, no validation errors). The monolith maps
     * java.lang.Exception globally
     * (src/main/webapp/WEB-INF/struts-config.xml:59-60), so a failure of the
     * insert reaches the same screen rather than a container error body; per
     * ADR-001 ("How parity is judged") the forwarded 200 becomes 500 here.
     */
    @Test
    void aFailedInsertAnswersWithTheErrorScreen() throws Exception {
        willThrow(new DataIntegrityViolationException("settlement_id in use"))
                .given(settlements).save(any(StoredSettlement.class));

        mvc.perform(post("/settlement/save")
                        .param("claimId", "119")
                        .param("coveredAmount", "5000.00")
                        .param("deductible", "500.00")
                        .param("depreciation", "0.00")
                        .param("user", "supervisor"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    /**
     * Rules: none — the monolith has no equivalent of a method mismatch to
     * reproduce, so the mapping leaves Spring's own 405 alone rather than
     * reporting the error screen for it.
     */
    @Test
    void anUnsupportedMethodKeepsItsOwnStatus() throws Exception {
        mvc.perform(get("/settlement/save"))
                .andExpect(status().isMethodNotAllowed());
    }
}
