package com.northstar.payment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoint tests for GET /payment/remittance against the seeded fixture. */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:payment-remittance-test",
        // The state the legacy capture had reached before the payment transcripts
        // were recorded (parity/preconditions/payment.sql; CHG-002, "Parity effect").
        "spring.sql.init.data-locations=file:../../src/main/resources/db/seed.sql,"
                + "file:../../parity/preconditions/payment.sql"})
class PaymentRemittanceEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Rules: PAY-R24, PAY-R26.
     * A claim with no payments shows remitClaimId, remitCount 0 and remitTotal
     * 0.00 and no per-payment fields; runs before the issue of this class
     * (src/main/java/com/northstar/claims/dao/PaymentDAO.java:122-137).
     */
    @Test
    @Order(1)
    void remittanceOfClaimWithoutPaymentsIsZero() throws Exception {
        mvc.perform(get("/payment/remittance").param("claimId", "119"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("remittance"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields.remitClaimId").value("119"))
                .andExpect(jsonPath("$.fields.remitCount").value("0"))
                .andExpect(jsonPath("$.fields.remitTotal").value("0.00"));
    }

    /**
     * Rules: PAY-R02, PAY-R25, PAY-R26.
     * After issuing 1000.00 for claim 119 (claimId omitted, so 119 by
     * default), the remittance lists remitCheck_61, remitDate_61,
     * remitAmount_61, remitStatus_61 and totals one payment of 1000.00
     * (src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:16-32).
     */
    @Test
    @Order(2)
    void remittanceListsAndTotalsTheIssuedPayment() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("payeeName", "Reserved Claimant")
                        .param("amount", "1000.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk());
        mvc.perform(get("/payment/remittance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.remitCheck_61").value("CHK-61"))
                .andExpect(jsonPath("$.fields.remitDate_61").value("2019-04-03"))
                .andExpect(jsonPath("$.fields.remitAmount_61").value("1000.00"))
                .andExpect(jsonPath("$.fields.remitStatus_61").value("ISSUED"))
                .andExpect(jsonPath("$.fields.remitClaimId").value("119"))
                .andExpect(jsonPath("$.fields.remitCount").value("1"))
                .andExpect(jsonPath("$.fields.remitTotal").value("1000.00"));
    }

    /**
     * Rules: PAY-R17, PAY-R25.
     * Seeded claim 1's single payment is listed with the seed's values
     * (src/main/resources/db/seed.sql:615).
     */
    @Test
    void remittanceListsSeededPayment() throws Exception {
        mvc.perform(get("/payment/remittance").param("claimId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.remitCheck_1").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.remitAmount_1").value("500.00"))
                .andExpect(jsonPath("$.fields.remitDate_1").value("2019-03-15"))
                .andExpect(jsonPath("$.fields.remitCount").value("1"))
                .andExpect(jsonPath("$.fields.remitTotal").value("500.00"));
    }
}
