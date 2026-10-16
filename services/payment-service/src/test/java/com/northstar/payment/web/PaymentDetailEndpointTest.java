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
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:payment-detail-test")
class PaymentDetailEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Rules: PAY-R22, PAY-R24, PAY-R28 (v1 of each).
     * Seeded payment 1 is shown with its six ns:field values formatted as
     * the JSP prints them (SPEC-PAY-001 live step L09;
     * src/main/webapp/WEB-INF/jsp/payment/detail.jsp:10-24).
     */
    @Test
    void detailShowsSeededPayment() throws Exception {
        mvc.perform(get("/payment/detail").param("paymentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("detail"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.length()").value(6))
                .andExpect(jsonPath("$.fields.detailPaymentId").value("1"))
                .andExpect(jsonPath("$.fields.detailPaymentClaim").value("1"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("500.00"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.detailPaymentDate").value("2019-03-15"))
                .andExpect(jsonPath("$.fields.detailPaymentStatus").value("ISSUED"));
    }

    /**
     * Rules: PAY-R22, PAY-R23 (v1 of each).
     * With no paymentId, or "abc", the default is 61: before any issue that
     * row does not exist and the answer is the error screen; after the issue
     * it is shown (SPEC-PAY-001 live steps L07, L10, L35).
     */
    @Test
    void detailDefaultsToPayment61() throws Exception {
        mvc.perform(get("/payment/detail"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty());
        mvc.perform(get("/payment/detail").param("paymentId", "abc"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Live Payee")
                        .param("paymentMethod", "wire"))
                .andExpect(status().isOk());
        mvc.perform(get("/payment/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.detailPaymentId").value("61"))
                .andExpect(jsonPath("$.fields.detailPaymentClaim").value("118"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("1118.00"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-61"))
                .andExpect(jsonPath("$.fields.detailPaymentDate").value("2019-04-03"))
                .andExpect(jsonPath("$.fields.detailPaymentStatus").value("ISSUED"));
    }

    /**
     * Rules: PAY-R23, PAY-R29 (v1 of each).
     * An unknown id is the error screen with no fields and no validation
     * errors (SPEC-PAY-001 live step L08).
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
