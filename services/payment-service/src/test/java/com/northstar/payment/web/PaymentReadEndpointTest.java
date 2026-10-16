package com.northstar.payment.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Endpoint tests for /payment/history, /payment/detail and /payment/remittance
 * against the seed (claim 1 has payment 1 of 500.00, CHK-00001).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.datasource.url=jdbc:hsqldb:mem:payment-read-test")
class PaymentReadEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private void insert(int id, int claim, String payee, double amount, String method) {
        jdbc.update("insert into PAYMENT values (?, ?, ?, ?, ?, ?, ?, '2019-04-03', 'ISSUED')",
                id, claim, claim, payee, amount, method, "CHK-" + id);
    }

    /**
     * Rules: PAY-R14, PAY-R15.
     * Live L25: claim 1 lists the seeded CHK-00001 500.00 then the new
     * payment, in id order, with the two fixed detail links.
     */
    @Test
    void historyListsTheClaimInIdOrderWithFixedLinks() throws Exception {
        insert(73, 1, "Get Payee", 5, "CHECK");
        mvc.perform(get("/payment/history").param("claimId", "1").param("user", "supervisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.checkNumber_1").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.paymentAmount_1").value("500.00"))
                .andExpect(jsonPath("$.fields.checkNumber_73").value("CHK-73"))
                .andExpect(jsonPath("$.fields.paymentAmount_73").value("5.00"))
                .andExpect(jsonPath("$.fields.paymentStatus_73").value("ISSUED"))
                .andExpect(jsonPath("$.links", contains("detail.do?paymentId=61",
                        "remittance.do?claimId=1", "detail.do?paymentId=61",
                        "remittance.do?claimId=1")));
    }

    /**
     * Rules: PAY-R14, PAY-R15.
     * Live L24, L26: no claimId is claim 119; a claim with no payments gives
     * an empty list and still the fixed links.
     */
    @Test
    void historyDefaultsTo119AndEmptyClaimIsEmpty() throws Exception {
        insert(61, 119, "Reserved Claimant", 0, "CHECK");
        mvc.perform(get("/payment/history").param("user", "supervisor"))
                .andExpect(jsonPath("$.fields.checkNumber_61").value("CHK-61"));
        mvc.perform(get("/payment/history").param("claimId", "9999").param("user", "supervisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.links[0]").value("detail.do?paymentId=61"))
                .andExpect(jsonPath("$.links[1]").value("remittance.do?claimId=9999"));
    }

    /**
     * Rules: PAY-R16, PAY-R18.
     * Live L28, L30, L31, L41: detail defaults to 61 (missing or unparseable)
     * and returns payee and method as stored.
     */
    @Test
    void detailDefaultsTo61AndShowsPayeeAndMethodAsStored() throws Exception {
        insert(61, 119, "Reserved Claimant", 0, "CHECK");
        insert(75, 120, "<b>A&B</b>", 1, "check");
        mvc.perform(get("/payment/detail").param("paymentId", "abc").param("user", "supervisor"))
                .andExpect(jsonPath("$.fields.detailPaymentId").value("61"))
                .andExpect(jsonPath("$.fields.detailPaymentClaim").value("119"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("0.00"))
                .andExpect(jsonPath("$.fields.detailPaymentDate").value("2019-04-03"));
        mvc.perform(get("/payment/detail").param("user", "supervisor"))
                .andExpect(jsonPath("$.fields.detailPaymentCheck").value("CHK-61"));
        mvc.perform(get("/payment/detail").param("paymentId", "75").param("user", "supervisor"))
                .andExpect(jsonPath("$.display.payeeName").value("<b>A&B</b>"))
                .andExpect(jsonPath("$.display.paymentMethod").value("check"))
                .andExpect(jsonPath("$.fields.detailPaymentStatus").value("ISSUED"));
    }

    /**
     * Rules: PAY-R17.
     * Live L29: an unknown paymentId is the error screen.
     */
    @Test
    void unknownPaymentIsTheErrorScreen() throws Exception {
        mvc.perform(get("/payment/detail").param("paymentId", "9999").param("user", "supervisor"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.screen").value("error"));
    }

    /**
     * Rules: PAY-R19, PAY-R20.
     * Live L33, L34: claim 1 totals 505.00 over its two payments; a claim with
     * no payments totals 0.00 with count 0.
     */
    @Test
    void remittanceListsRowsCountAndTotal() throws Exception {
        insert(73, 1, "Get Payee", 5, "CHECK");
        mvc.perform(get("/payment/remittance").param("claimId", "1").param("user", "supervisor"))
                .andExpect(jsonPath("$.fields.remitCheck_1").value("CHK-00001"))
                .andExpect(jsonPath("$.fields.remitDate_1").value("2019-03-15"))
                .andExpect(jsonPath("$.fields.remitAmount_73").value("5.00"))
                .andExpect(jsonPath("$.fields.remitStatus_73").value("ISSUED"))
                .andExpect(jsonPath("$.fields.remitClaimId").value("1"))
                .andExpect(jsonPath("$.fields.remitCount").value("2"))
                .andExpect(jsonPath("$.fields.remitTotal").value("505.00"))
                .andExpect(jsonPath("$.display.payeeName_73").value("Get Payee"));
        mvc.perform(get("/payment/remittance").param("claimId", "9999").param("user", "supervisor"))
                .andExpect(jsonPath("$.fields.remitCount").value("0"))
                .andExpect(jsonPath("$.fields.remitTotal").value("0.00"));
    }

    /**
     * Rules: PAY-R19, PAY-R20.
     * Live L32: no claimId is claim 119, and one NaN amount makes the total NaN.
     */
    @Test
    void remittanceDefaultsTo119AndNanPoisonsTheTotal() throws Exception {
        insert(61, 119, "Reserved Claimant", 10, "CHECK");
        insert(62, 119, "Reserved Claimant", Double.NaN, "CHECK");
        mvc.perform(get("/payment/remittance").param("user", "supervisor"))
                .andExpect(jsonPath("$.fields.remitClaimId").value("119"))
                .andExpect(jsonPath("$.fields.remitAmount_62").value("NaN"))
                .andExpect(jsonPath("$.fields.remitTotal").value("NaN"));
    }

    /**
     * Rules: PAY-R13.
     * Live L01, L39, L40: history, detail and remittance without a user are
     * redirected to /claims/login.do.
     */
    @Test
    void readsWithoutUserRedirectToLogin() throws Exception {
        for (String path : new String[] {"/payment/history", "/payment/detail",
                "/payment/remittance"}) {
            mvc.perform(get(path))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "/claims/login.do"));
        }
    }
}
