package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Endpoint tests for /payment/issue against the seeded fixture (payments up to
 * id 60, settlement 119 of 0.00, settlement 120 of claim 120). Each test rolls
 * back, so each starts from the seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.datasource.url=jdbc:hsqldb:mem:payment-issue-test")
class PaymentIssueEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * A supervisor issue for claim 119. {@code overrides} are name, value
     * pairs; a null value drops the parameter.
     */
    private MockHttpServletRequestBuilder issue(String amount, String... overrides) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("claimId", "119");
        params.put("payeeName", "Reserved Claimant");
        params.put("paymentMethod", "CHECK");
        params.put("user", "supervisor");
        params.put("amount", amount);
        for (int i = 0; i < overrides.length; i += 2) {
            params.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder request = post("/payment/issue");
        params.forEach((name, value) -> {
            if (value != null) {
                request.param(name, value);
            }
        });
        return request;
    }

    private Map<String, Object> payment(int id) {
        return jdbc.queryForMap("select * from PAYMENT where payment_id = ?", id);
    }

    private int paymentCount() {
        return jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
    }

    private void saveNewerSettlementFor119() {
        jdbc.update("insert into SETTLEMENT values (121, 119, 5000.0, 500.0, 0.0, TRUE, "
                + "1000.0, 'supervisor', '2019-04-01')");
    }

    /**
     * Rules: PAY-R01, PAY-R04, PAY-R06, PAY-R07, PAY-R08.
     * Live L04: amount omitted on the seed falls back to settlement 119 (0.00);
     * the first payment is 61, CHK-61, ISSUED, issued 2019-04-03.
     */
    @Test
    void omittedAmountOnTheSeedIsSettlement119AndTheFirstIdIs61() throws Exception {
        mvc.perform(issue(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("0.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"))
                .andExpect(jsonPath("$.fields.paymentStatus").value("ISSUED"));
        Map<String, Object> row = payment(61);
        assertThat(row.get("CLAIM_ID")).isEqualTo(119);
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(119);
        assertThat(row.get("ISSUED_DATE").toString()).isEqualTo("2019-04-03");
        assertThat(row.get("STATUS")).isEqualTo("ISSUED");
    }

    /**
     * Rules: PAY-R03, PAY-R04.
     * Live L06, L07 and transcript payment_issue: once a newer settlement of
     * 1000.00 exists the payment links to it, and an omitted or unparseable
     * amount takes its amount.
     */
    @Test
    void newestSettlementIsLinkedAndSuppliesTheFallbackAmount() throws Exception {
        saveNewerSettlementFor119();
        mvc.perform(issue(null)).andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        mvc.perform(issue("abc")).andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        assertThat(payment(61).get("SETTLEMENT_ID")).isEqualTo(121);
        assertThat(payment(62).get("SETTLEMENT_ID")).isEqualTo(121);
    }

    /**
     * Rules: PAY-R05.
     * Live L08 to L12: negative, half-cent, above-settlement, NaN and exponent
     * amounts are all accepted and stored unrounded.
     */
    @Test
    void anyParseableAmountIsStoredAsSent() throws Exception {
        mvc.perform(issue("-250.00"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("-250.00"));
        mvc.perform(issue("1.005")).andExpect(jsonPath("$.fields.paymentAmount").value("1.01"));
        mvc.perform(issue("5000.00"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("5000.00"));
        mvc.perform(issue("NaN")).andExpect(jsonPath("$.fields.paymentAmount").value("NaN"));
        mvc.perform(issue("1e3")).andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        assertThat(((Number) payment(62).get("AMOUNT")).doubleValue()).isEqualTo(1.005);
        assertThat(((Number) payment(64).get("AMOUNT")).doubleValue()).isNaN();
    }

    /**
     * Rules: PAY-R06, PAY-R07.
     * Live L13: a checkNumber parameter is ignored; the id is max+1 and the
     * check number follows it.
     */
    @Test
    void checkNumberParameterIsIgnored() throws Exception {
        mvc.perform(issue("10.00", "checkNumber", "MY-CHECK-1"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"));
        mvc.perform(issue("10.00")).andExpect(jsonPath("$.fields.checkNumber").value("CHK-62"));
    }

    /**
     * Rules: PAY-R09.
     * Live L14, L23: method and payee are stored exactly as sent.
     */
    @Test
    void payeeAndMethodAreStoredAsSent() throws Exception {
        mvc.perform(issue("10.00", "paymentMethod", "check")).andExpect(status().isOk());
        mvc.perform(issue("1.00", "payeeName", "<b>A&B</b>")).andExpect(status().isOk());
        assertThat(payment(61).get("PAYMENT_METHOD")).isEqualTo("check");
        assertThat(payment(62).get("PAYEE_NAME")).isEqualTo("<b>A&B</b>");
    }

    /**
     * Rules: PAY-R10.
     * Live L15, L16, L17: no method, no payee, or a method over 20 characters
     * fails the insert; the error screen comes back and nothing is written.
     */
    @Test
    void missingPayeeOrMethodOrLongMethodIsTheErrorScreen() throws Exception {
        int before = paymentCount();
        mvc.perform(post("/payment/issue").param("claimId", "119").param("amount", "10.00")
                        .param("payeeName", "Reserved Claimant").param("user", "supervisor"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty());
        mvc.perform(post("/payment/issue").param("claimId", "119").param("amount", "10.00")
                        .param("paymentMethod", "CHECK").param("user", "supervisor"))
                .andExpect(status().isInternalServerError());
        mvc.perform(issue("10.00", "paymentMethod", "ELECTRONIC-FUNDS-XFER"))
                .andExpect(status().isInternalServerError());
        assertThat(paymentCount()).isEqualTo(before);
    }

    /**
     * Rules: PAY-R11.
     * Live L18: claim 9999 has no settlement; error screen, nothing written.
     */
    @Test
    void claimWithoutSettlementIsTheErrorScreen() throws Exception {
        int before = paymentCount();
        mvc.perform(issue("10.00", "claimId", "9999"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        assertThat(paymentCount()).isEqualTo(before);
    }

    /**
     * Rules: PAY-R02.
     * Live L19, L20: claimId omitted or unparseable is 119.
     */
    @Test
    void omittedOrUnparseableClaimIdIs119() throws Exception {
        mvc.perform(post("/payment/issue").param("amount", "11.00").param("payeeName", "P")
                .param("paymentMethod", "CHECK").param("user", "supervisor"));
        mvc.perform(issue("12.00", "claimId", "abc"));
        assertThat(payment(61).get("CLAIM_ID")).isEqualTo(119);
        assertThat(payment(62).get("CLAIM_ID")).isEqualTo(119);
    }

    /**
     * Rules: PAY-R12.
     * Live L21, L22, L36: a GET issues, the CLOSED claim 120 is paid against
     * settlement 120, and an adjuster can issue.
     */
    @Test
    void getClosedClaimAndAdjusterAreNotRestricted() throws Exception {
        mvc.perform(get("/payment/issue").param("claimId", "1").param("payeeName", "Get Payee")
                        .param("amount", "5").param("paymentMethod", "CHECK")
                        .param("user", "supervisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("5.00"));
        mvc.perform(issue("20.00", "claimId", "120", "user", "adjuster7"))
                .andExpect(status().isOk());
        assertThat(payment(61).get("SETTLEMENT_ID")).isEqualTo(1);
        assertThat(payment(62).get("SETTLEMENT_ID")).isEqualTo(120);
    }

    /**
     * Rules: PAY-R13.
     * Live L38: without a user the issue request is redirected to
     * /claims/login.do and writes nothing.
     */
    @Test
    void noUserRedirectsToLoginAndWritesNothing() throws Exception {
        int before = paymentCount();
        mvc.perform(post("/payment/issue").param("claimId", "120").param("amount", "3.00")
                        .param("payeeName", "Reserved Claimant").param("paymentMethod", "CHECK"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/claims/login.do"));
        assertThat(paymentCount()).isEqualTo(before);
    }
}
