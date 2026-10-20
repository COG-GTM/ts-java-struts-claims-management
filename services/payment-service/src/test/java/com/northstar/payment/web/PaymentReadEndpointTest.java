package com.northstar.payment.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Endpoint tests for history, detail, remittance and the operator rule. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:payment-read-test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentReadEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder as(String operator, MockHttpServletRequestBuilder r) {
        return r.header(OperatorFilter.HEADER, operator);
    }

    /** PAY-R16 (v1). live:adjuster-issue#1: adjuster7 issues payment 61 and is shown as operator. */
    @Test
    void anyOperatorCanIssue() throws Exception {
        mvc.perform(as("adjuster7", post("/payment/issue")).param("claimId", "119")
                        .param("payeeName", "Reserved Claimant").param("amount", "1000.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"))
                .andExpect(jsonPath("$.operator").value("adjuster7"));
    }

    /**
     * PAY-R17 (v1), PAY-R18 (v1). live:history-order#1, live:history#1-#2: rows ordered by
     * payment_id (2, 200, 300), claimId falls back to 119, and the two links are printed
     * twice with the fixed paymentId 61 (history.jsp:16-32).
     */
    @Test
    void historyOrderAndFixedLinks() throws Exception {
        jdbc.update("INSERT INTO PAYMENT VALUES (300, 2, 2, 'Third', 3, 'CHECK', 'CHK-300', '2019-03-20', 'ISSUED')");
        jdbc.update("INSERT INTO PAYMENT VALUES (200, 2, 2, 'Second', 2, 'CHECK', 'CHK-200', '2019-03-19', 'ISSUED')");
        mvc.perform(as("supervisor", get("/payment/history")).param("claimId", "2"))
                .andExpect(jsonPath("$.screen").value("history"))
                .andExpect(jsonPath("$.fields.keys()").value(contains(
                        "checkNumber_2", "paymentAmount_2", "paymentStatus_2",
                        "checkNumber_200", "paymentAmount_200", "paymentStatus_200",
                        "checkNumber_300", "paymentAmount_300", "paymentStatus_300")))
                .andExpect(jsonPath("$.links").value(contains("detail.do?paymentId=61",
                        "remittance.do?claimId=2", "detail.do?paymentId=61", "remittance.do?claimId=2")));
        mvc.perform(as("supervisor", get("/payment/history")).param("claimId", "abc"))
                .andExpect(jsonPath("$.links[1]").value("remittance.do?claimId=119"))
                .andExpect(jsonPath("$.fields").isEmpty());
    }

    /**
     * PAY-R19 (v1), PAY-R25 (v1). live:detail#1, live:issue-raw-text#2: detail fields of
     * payment 1, and payee and method as the screen shows them (the text, not markup).
     */
    @Test
    void detailShowsOnePayment() throws Exception {
        mvc.perform(as("supervisor", get("/payment/detail")).param("paymentId", "1"))
                .andExpect(jsonPath("$.fields.detailPaymentId").value("1"))
                .andExpect(jsonPath("$.fields.detailPaymentClaim").value("1"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("500.00"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.detailPaymentDate").value("2019-03-15"))
                .andExpect(jsonPath("$.fields.detailPaymentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.display.payeeName").value("Claimant 1"))
                .andExpect(jsonPath("$.display.paymentMethod").value("CHECK"));
    }

    /**
     * PAY-R20 (v1), PAY-R21 (v1). live:detail-default-61#1, live:detail#2-#4: paymentId
     * falls back to 61; when that payment (or 99999) does not exist the error screen.
     */
    @Test
    void detailFallsBackTo61AndErrorsWhenMissing() throws Exception {
        mvc.perform(as("supervisor", get("/payment/detail")))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        mvc.perform(as("supervisor", get("/payment/detail")).param("paymentId", "99999"))
                .andExpect(status().is5xxServerError());
        jdbc.update("INSERT INTO PAYMENT VALUES (61, 5, 5, 'Payee 61', 61, 'EFT', 'CHK-61X', '2019-03-21', 'ISSUED')");
        mvc.perform(as("supervisor", get("/payment/detail")).param("paymentId", "abc"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-61X"));
    }

    /**
     * PAY-R22 (v1), PAY-R23 (v1). live:remittance#1-#3, live:remittance-total#1: rows,
     * count and a total over every status (500 ISSUED + 100 VOID = 600.00); claimId
     * falls back to 119 with count 0 and total 0.00.
     */
    @Test
    void remittanceCountAndTotal() throws Exception {
        jdbc.update("INSERT INTO PAYMENT VALUES (400, 3, 3, 'Void Payee', 100, 'CHECK', 'CHK-400', '2019-03-22', 'VOID')");
        mvc.perform(as("supervisor", get("/payment/remittance")).param("claimId", "3"))
                .andExpect(jsonPath("$.fields.remitStatus_400").value("VOID"))
                .andExpect(jsonPath("$.display.remitPayee_400").value("Void Payee"))
                .andExpect(jsonPath("$.fields.remitCount").value("2"))
                .andExpect(jsonPath("$.fields.remitTotal").value("600.00"));
        mvc.perform(as("supervisor", get("/payment/remittance")))
                .andExpect(jsonPath("$.fields.remitClaimId").value("119"))
                .andExpect(jsonPath("$.fields.remitCount").value("0"))
                .andExpect(jsonPath("$.fields.remitTotal").value("0.00"));
    }

    /** PAY-R26 (v1). live:unauthenticated#3-#5: history, detail and remittance redirect too. */
    @Test
    void readsRedirectWithoutOperator() throws Exception {
        for (String path : new String[] {"/payment/history", "/payment/detail", "/payment/remittance"}) {
            mvc.perform(get(path)).andExpect(status().isFound())
                    .andExpect(header().string("Location", "/claims/login.do"));
        }
    }
}
