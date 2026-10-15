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

/** Endpoint tests for GET /payment/detail against the seeded fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:payment-detail-test",
        // The state the legacy capture had reached before the payment transcripts
        // were recorded (parity/preconditions/payment.sql; CHG-002, "Parity effect").
        "spring.sql.init.data-locations=file:../../src/main/resources/db/seed.sql,"
                + "file:../../parity/preconditions/payment.sql"})
class PaymentDetailEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Rules: PAY-R21, PAY-R23, PAY-R27, PAY-R28.
     * Seeded payment 1 is shown with its six fields formatted as the JSP
     * prints them (src/main/webapp/WEB-INF/jsp/payment/detail.jsp:10-24;
     * verified against the running legacy app, paymentId=1).
     */
    @Test
    void detailShowsSeededPayment() throws Exception {
        mvc.perform(get("/payment/detail").param("paymentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("detail"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.detailPaymentId").value("1"))
                .andExpect(jsonPath("$.fields.detailPaymentClaim").value("1"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("500.00"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.detailPaymentDate").value("2019-03-15"))
                .andExpect(jsonPath("$.fields.detailPaymentStatus").value("ISSUED"));
    }

    /**
     * Rules: PAY-R20, PAY-R22.
     * With no paymentId the default is 61: before any issue that row does not
     * exist and the answer is the error screen; after the issue it is shown.
     */
    @Test
    void detailDefaultsToPayment61() throws Exception {
        mvc.perform(get("/payment/detail"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty());
        mvc.perform(post("/payment/issue")
                        .param("claimId", "119")
                        .param("payeeName", "Reserved Claimant")
                        .param("amount", "1000.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk());
        mvc.perform(get("/payment/detail").param("paymentId", "not a number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.detailPaymentId").value("61"))
                .andExpect(jsonPath("$.fields.detailPaymentClaim").value("119"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-61"))
                .andExpect(jsonPath("$.fields.detailPaymentDate").value("2019-04-03"))
                .andExpect(jsonPath("$.fields.detailPaymentStatus").value("ISSUED"));
    }

    /**
     * Rules: PAY-R22.
     * An unknown id is the error screen with no fields and no validation
     * errors (verified against the running legacy app, paymentId=9999).
     */
    @Test
    void detailOfUnknownPaymentIsAnErrorScreen() throws Exception {
        mvc.perform(get("/payment/detail").param("paymentId", "9999"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
}
