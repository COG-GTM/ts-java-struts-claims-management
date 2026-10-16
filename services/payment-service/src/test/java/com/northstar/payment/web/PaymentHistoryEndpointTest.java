package com.northstar.payment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoint tests for GET /payment/history against the seeded fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:payment-history-test")
class PaymentHistoryEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Rules: PAY-R18, PAY-R19, PAY-R21 (v1 of each).
     * Transcript: payment_history — after payment_issue, GET with no
     * parameters lists claim 119's one payment as checkNumber_61 CHK-61,
     * paymentAmount_61 1000.00, paymentStatus_61 ISSUED, and no count or
     * total (src/main/webapp/WEB-INF/jsp/payment/history.jsp:26-32). The issue
     * needs the capture's state, settlement 121 for claim 119 at 1000.00
     * (parity/preconditions/payment/capture-state.sql), since PAY-R07 (v2)
     * would reject 1000.00 against the seeded 0.00 settlement.
     */
    @Test
    void historyListsPayment61AfterIssue() throws Exception {
        mvc.perform(get("/payment/history").param("claimId", "119"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("history"))
                .andExpect(jsonPath("$.fields").isEmpty());
        jdbc.update("insert into SETTLEMENT values (121, 119, 5000, 500, 0, TRUE, 1000, 'supervisor', '2019-04-01')");
        mvc.perform(post("/payment/issue")
                        .param("claimId", "119")
                        .param("payeeName", "Reserved Claimant")
                        .param("amount", "1000.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk());
        mvc.perform(get("/payment/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("history"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields.checkNumber_61").value("CHK-61"))
                .andExpect(jsonPath("$.fields.paymentAmount_61").value("1000.00"))
                .andExpect(jsonPath("$.fields.paymentStatus_61").value("ISSUED"));
    }

    /**
     * Rules: PAY-R19 (v1).
     * Seeded claim 1's payment is listed with the seed's own zero-padded check
     * number (SPEC-PAY-001 live step L05; src/main/resources/db/seed.sql:615).
     */
    @Test
    void historyListsSeededPayment() throws Exception {
        mvc.perform(get("/payment/history").param("claimId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.checkNumber_1").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.paymentAmount_1").value("500.00"))
                .andExpect(jsonPath("$.fields.paymentStatus_1").value("ISSUED"));
    }

    /**
     * Rules: PAY-R18, PAY-R19 (v1 of each).
     * A claim with no payments, or an unparseable claimId (119 by default,
     * which has none on a fresh seed), yields status 200 and no fields
     * (SPEC-PAY-001 live steps L04, L06).
     */
    @Test
    void historyOfClaimWithoutPaymentsIsEmpty() throws Exception {
        mvc.perform(get("/payment/history").param("claimId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("history"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
        mvc.perform(get("/payment/history").param("claimId", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields").isEmpty());
    }
}
