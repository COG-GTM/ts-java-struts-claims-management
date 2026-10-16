package com.northstar.payment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoint tests for /payment/history, /payment/detail and
 * /payment/remittance against the seed plus payments 61 (0.00), 62 (NaN) and
 * 63 (payee markup, method "check") on claim 119, the shape live L24-L34 of
 * docs/specs/SPEC-PAY-001-live.json read back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:payment-read-test")
class PaymentReadEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void payments61To63() {
        jdbc.update("delete from PAYMENT where payment_id > 60");
        jdbc.update("insert into PAYMENT values (61, 119, 119, 'Reserved Claimant', 0,"
                + " 'CHECK', 'CHK-61', '2019-04-03', 'ISSUED')");
        jdbc.update("insert into PAYMENT values (62, 119, 119, 'Reserved Claimant', ?,"
                + " 'CHECK', 'CHK-62', '2019-04-03', 'ISSUED')", Double.NaN);
        jdbc.update("insert into PAYMENT values (63, 119, 119, '<b>A&B</b>', 1,"
                + " 'check', 'CHK-63', '2019-04-03', 'ISSUED')");
    }

    /**
     * Rules: PAY-R14, PAY-R15. Transcript: payment_history (checkNumber_61,
     * paymentAmount_61, paymentStatus_61 with no claimId). Rows are in
     * payment_id order and the links are detail.do?paymentId=61 and
     * remittance.do?claimId=119, twice (live L24;
     * src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19).
     */
    @Test
    void historyDefaultsToClaim119WithFixedLinks() throws Exception {
        mvc.perform(get("/payment/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("history"))
                .andExpect(jsonPath("$.fields.checkNumber_61").value("CHK-61"))
                .andExpect(jsonPath("$.fields.paymentAmount_61").value("0.00"))
                .andExpect(jsonPath("$.fields.paymentStatus_61").value("ISSUED"))
                .andExpect(jsonPath("$.fields.paymentAmount_62").value("NaN"))
                .andExpect(jsonPath("$.fields.length()").value(9))
                .andExpect(jsonPath("$.links[0]").value("detail.do?paymentId=61"))
                .andExpect(jsonPath("$.links[1]").value("remittance.do?claimId=119"))
                .andExpect(jsonPath("$.links[2]").value("detail.do?paymentId=61"))
                .andExpect(jsonPath("$.links[3]").value("remittance.do?claimId=119"))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    /**
     * Rule: PAY-R15. For claim 1 the detail link still names payment 61 and
     * the remittance link names claim 1 (live L25, L26; history.jsp:16-19).
     */
    @Test
    void historyLinksAlwaysNamePayment61() throws Exception {
        mvc.perform(get("/payment/history").param("claimId", "1"))
                .andExpect(jsonPath("$.fields.checkNumber_1").value("CHK-00001"))
                .andExpect(jsonPath("$.links[0]").value("detail.do?paymentId=61"))
                .andExpect(jsonPath("$.links[1]").value("remittance.do?claimId=1"));
    }

    /**
     * Rules: PAY-R16, PAY-R18. Detail of payment 63 shows the six fields and
     * the payee escaped as bean:write prints it, the method lower-case
     * (live L27, L30 payee_markup, L31;
     * src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14,18).
     */
    @Test
    void detailShowsFieldsAndEscapedPayee() throws Exception {
        mvc.perform(get("/payment/detail").param("paymentId", "63"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("detail"))
                .andExpect(jsonPath("$.fields.detailPaymentId").value("63"))
                .andExpect(jsonPath("$.fields.detailPaymentClaim").value("119"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("1.00"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-63"))
                .andExpect(jsonPath("$.fields.detailPaymentDate").value("2019-04-03"))
                .andExpect(jsonPath("$.fields.detailPaymentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.display.payee").value("&lt;b&gt;A&amp;B&lt;/b&gt;"))
                .andExpect(jsonPath("$.display.method").value("check"));
    }

    /**
     * Rules: PAY-R16, PAY-R17. No paymentId (live L28) and paymentId=abc
     * (live L41) are payment 61; paymentId=9999 is the error screen (live L29;
     * src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24).
     */
    @Test
    void detailDefaultsTo61AndUnknownIsAnError() throws Exception {
        mvc.perform(get("/payment/detail"))
                .andExpect(jsonPath("$.fields.detailPaymentId").value("61"));
        mvc.perform(get("/payment/detail").param("paymentId", "abc"))
                .andExpect(jsonPath("$.fields.detailPaymentId").value("61"));
        mvc.perform(get("/payment/detail").param("paymentId", "9999"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
    }

    /**
     * Rules: PAY-R19, PAY-R20. Remittance with no claimId is claim 119: three
     * rows, remitCount 3 and remitTotal NaN because sum(amount) includes the
     * NaN payment (live L32; src/main/java/com/northstar/claims/dao/PaymentDAO.java:121-136).
     * Claim 1 totals its seeded 500.00 plus 5.00 (live L33) and claim 9999
     * has no rows, remitCount 0 and remitTotal 0.00 (live L34).
     */
    @Test
    void remittanceTotalsStoredAmounts() throws Exception {
        mvc.perform(get("/payment/remittance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("remittance"))
                .andExpect(jsonPath("$.fields.remitCheck_61").value("CHK-61"))
                .andExpect(jsonPath("$.fields.remitDate_61").value("2019-04-03"))
                .andExpect(jsonPath("$.fields.remitAmount_62").value("NaN"))
                .andExpect(jsonPath("$.fields.remitStatus_63").value("ISSUED"))
                .andExpect(jsonPath("$.display.payee_63").value("&lt;b&gt;A&amp;B&lt;/b&gt;"))
                .andExpect(jsonPath("$.fields.remitClaimId").value("119"))
                .andExpect(jsonPath("$.fields.remitCount").value("3"))
                .andExpect(jsonPath("$.fields.remitTotal").value("NaN"));
        jdbc.update("insert into PAYMENT values (64, 1, 1, 'Reserved Claimant', 5,"
                + " 'CHECK', 'CHK-64', '2019-04-03', 'ISSUED')");
        mvc.perform(get("/payment/remittance").param("claimId", "1"))
                .andExpect(jsonPath("$.fields.remitCount").value("2"))
                .andExpect(jsonPath("$.fields.remitTotal").value("505.00"));
        mvc.perform(get("/payment/remittance").param("claimId", "9999"))
                .andExpect(jsonPath("$.fields.remitCount").value("0"))
                .andExpect(jsonPath("$.fields.remitTotal").value("0.00"))
                .andExpect(jsonPath("$.fields.length()").value(3));
    }
}
