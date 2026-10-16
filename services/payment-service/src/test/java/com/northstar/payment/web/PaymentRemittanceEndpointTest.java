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
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:payment-remittance-test")
class PaymentRemittanceEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Rules: PAY-R25, PAY-R27 (v1 of each).
     * With no claimId, or "abc", the claim is 119, which has no payments on a
     * fresh seed: remitClaimId 119, remitCount 0, remitTotal 0.00 and no
     * per-payment field (SPEC-PAY-001 live steps L11, L13;
     * src/main/java/com/northstar/claims/dao/PaymentDAO.java:122-137).
     */
    @Test
    @Order(1)
    void remittanceOfClaimWithoutPaymentsIsZero() throws Exception {
        mvc.perform(get("/payment/remittance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("remittance"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields.remitClaimId").value("119"))
                .andExpect(jsonPath("$.fields.remitCount").value("0"))
                .andExpect(jsonPath("$.fields.remitTotal").value("0.00"));
        mvc.perform(get("/payment/remittance").param("claimId", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.remitClaimId").value("119"));
    }

    /**
     * Rules: PAY-R26, PAY-R27 (v1 of each).
     * After 1118.00, 1118.00 and 1.00 are issued for claim 118 the remittance
     * lists remitCheck/remitDate/remitAmount/remitStatus for payments 61, 62,
     * 63 in id order and totals 2237.00 (SPEC-PAY-001 live step L24;
     * src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:16-32).
     */
    @Test
    @Order(2)
    void remittanceListsAndTotalsTheIssuedPayments() throws Exception {
        for (String[] issue : new String[][] {{"Live Payee", null}, {"Second Payee", "abc"}, {"", "1"}}) {
            var request = post("/payment/issue").param("claimId", "118")
                    .param("payeeName", issue[0]).param("paymentMethod", "CHECK");
            if (issue[1] != null) {
                request = request.param("amount", issue[1]);
            }
            mvc.perform(request).andExpect(status().isOk());
        }
        mvc.perform(get("/payment/remittance").param("claimId", "118"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(15))
                .andExpect(jsonPath("$.fields.remitCheck_61").value("CHK-61"))
                .andExpect(jsonPath("$.fields.remitDate_61").value("2019-04-03"))
                .andExpect(jsonPath("$.fields.remitAmount_61").value("1118.00"))
                .andExpect(jsonPath("$.fields.remitStatus_61").value("ISSUED"))
                .andExpect(jsonPath("$.fields.remitAmount_62").value("1118.00"))
                .andExpect(jsonPath("$.fields.remitAmount_63").value("1.00"))
                .andExpect(jsonPath("$.fields.remitClaimId").value("118"))
                .andExpect(jsonPath("$.fields.remitCount").value("3"))
                .andExpect(jsonPath("$.fields.remitTotal").value("2237.00"));
    }

    /**
     * Rules: PAY-R26, PAY-R27 (v1 of each).
     * Seeded claim 1's single payment is listed with the seed's values and
     * totalled (SPEC-PAY-001 live step L12; src/main/resources/db/seed.sql:615).
     */
    @Test
    void remittanceListsSeededPayment() throws Exception {
        mvc.perform(get("/payment/remittance").param("claimId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.remitCheck_1").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.remitAmount_1").value("500.00"))
                .andExpect(jsonPath("$.fields.remitDate_1").value("2019-03-15"))
                .andExpect(jsonPath("$.fields.remitStatus_1").value("ISSUED"))
                .andExpect(jsonPath("$.fields.remitCount").value("1"))
                .andExpect(jsonPath("$.fields.remitTotal").value("500.00"));
    }
}
