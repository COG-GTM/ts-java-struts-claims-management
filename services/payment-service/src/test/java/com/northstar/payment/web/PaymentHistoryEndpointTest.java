package com.northstar.payment.web;

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

/** Endpoint tests for GET /payment/history against the seeded fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:payment-history-test",
        // The state the legacy capture had reached before the payment transcripts
        // were recorded (parity/preconditions/payment.sql; CHG-002, "Parity effect").
        "spring.sql.init.data-locations=file:../../src/main/resources/db/seed.sql,"
                + "file:../../parity/preconditions/payment.sql"})
class PaymentHistoryEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Rules: PAY-R16, PAY-R18, PAY-R19.
     * Transcript: payment_history — after payment_issue, GET with no
     * parameters lists claim 119's one payment as checkNumber_61 CHK-61,
     * paymentAmount_61 1000.00, paymentStatus_61 ISSUED, and nothing else.
     */
    @Test
    void historyListsPayment61AfterIssue() throws Exception {
        mvc.perform(get("/payment/history").param("claimId", "119"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("history"))
                .andExpect(jsonPath("$.fields").isEmpty());
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
     * Rules: PAY-R17, PAY-R18.
     * Seeded payments are listed by ascending payment_id with the seed's own
     * zero-padded check numbers (src/main/resources/db/seed.sql:615-674).
     */
    @Test
    void historyListsSeededPaymentsInIdOrder() throws Exception {
        mvc.perform(get("/payment/history").param("claimId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.checkNumber_1").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.paymentAmount_1").value("500.00"))
                .andExpect(jsonPath("$.fields.paymentStatus_1").value("ISSUED"));
    }

    /**
     * Rules: PAY-R02, PAY-R18.
     * An unparseable claimId is claim 119; a claim with no payments yields no
     * fields and is not an error.
     */
    @Test
    void historyOfClaimWithoutPaymentsIsEmpty() throws Exception {
        mvc.perform(get("/payment/history").param("claimId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("history"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
}
