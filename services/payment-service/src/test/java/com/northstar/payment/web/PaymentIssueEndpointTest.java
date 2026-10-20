package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Endpoint tests for /payment/issue against the seeded fixture (60 payments, ids 1-60). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:payment-issue-test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentIssueEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder issue(String claimId, String payee, String amount,
            String method) {
        MockHttpServletRequestBuilder request = post("/payment/issue")
                .header(OperatorFilter.HEADER, "supervisor");
        if (claimId != null) {
            request.param("claimId", claimId);
        }
        if (payee != null) {
            request.param("payeeName", payee);
        }
        if (amount != null) {
            request.param("amount", amount);
        }
        if (method != null) {
            request.param("paymentMethod", method);
        }
        return request;
    }

    private Map<String, Object> payment(int id) {
        return jdbc.queryForMap("select * from PAYMENT where payment_id = ?", id);
    }

    /**
     * PAY-R01 (v1), PAY-R11 (v1). Transcript payment_issue;
     * live:issue-post#1: fields paymentAmount 1000.00, checkNumber CHK-61, paymentStatus
     * ISSUED; payment and claim ids not shown; row 61 dated 2019-04-03
     * (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-43).
     */
    @Test
    void postIssuesPayment61ForClaim119() throws Exception {
        mvc.perform(issue("119", "Reserved Claimant", "1000.00", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"))
                .andExpect(jsonPath("$.fields.paymentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.fields.paymentId").doesNotExist())
                .andExpect(jsonPath("$.fields.claimId").doesNotExist())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.operator").value("supervisor"));
        Map<String, Object> row = payment(61);
        assertThat(row.get("CLAIM_ID")).isEqualTo(119);
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(119);
        assertThat(row.get("ISSUED_DATE").toString()).isEqualTo("2019-04-03");
        assertThat(row.get("STATUS")).isEqualTo("ISSUED");
    }

    /** PAY-R02 (v1). live:issue-get#1: GET with query parameters also writes payment 61. */
    @Test
    void getAlsoIssues() throws Exception {
        mvc.perform(get("/payment/issue").header(OperatorFilter.HEADER, "supervisor")
                        .param("claimId", "1").param("payeeName", "Get Payee")
                        .param("amount", "10").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("10.00"));
        assertThat(payment(61).get("PAYEE_NAME")).isEqualTo("Get Payee");
    }

    /**
     * PAY-R03 (v1), PAY-R04 (v1). live:issue-claim-fallback#1-#3: claimId abc, missing
     * and 1.5 all fall back to claim 119 (ClaimsActionSupport.java:31-37).
     */
    @Test
    void claimIdFallsBackTo119() throws Exception {
        mvc.perform(issue("abc", "Fallback A", "5", "CHECK")).andExpect(status().isOk());
        mvc.perform(issue(null, "Fallback B", "6", "CHECK")).andExpect(status().isOk());
        mvc.perform(issue("1.5", "Fallback C", "7", "CHECK")).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select claim_id from PAYMENT where payment_id > 60",
                Integer.class)).containsExactly(119, 119, 119);
    }

    /**
     * PAY-R05 (v1). live:issue-newest-settlement#1: with settlement 121 of 1000 added,
     * the payment takes settlement_id 121 and amount 1000 (SettlementDAO.java:100-115).
     */
    @Test
    void newestSettlementSuppliesIdAndAmount() throws Exception {
        jdbc.update("INSERT INTO SETTLEMENT VALUES (121, 119, 1500, 500, 0, FALSE, 1000,"
                + " 'adjuster1', '2019-03-02')");
        mvc.perform(issue("119", "Newest Settlement", null, "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        assertThat(payment(61).get("SETTLEMENT_ID")).isEqualTo(121);
    }

    /**
     * PAY-R06 (v1). live:issue-no-settlement#1-#2: claim 120 without its settlement and
     * unknown claim 9999 end on the error screen and write nothing.
     */
    @Test
    void noSettlementIsErrorAndWritesNothing() throws Exception {
        jdbc.update("DELETE FROM SETTLEMENT WHERE settlement_id = 120");
        mvc.perform(issue("120", "Reserved Claimant", "1000.00", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        mvc.perform(issue("9999", "Reserved Claimant", "1000.00", "CHECK"))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(60);
    }

    /**
     * PAY-R07 (v1), PAY-R08 (v1), PAY-R24 (v1). live:issue-amount-fallback-seed#1-#3 and
     * live:issue-amount-raw#1-#5: missing or unparsable amount takes the settlement
     * amount (0.00 for claim 119); -50, 1.005, 1e3 and NaN are stored as parsed and
     * printed with %.2f (1.01, NaN).
     */
    @Test
    void amountFallbackAndRawValues() throws Exception {
        mvc.perform(issue("119", "No Amount", null, "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("0.00"));
        mvc.perform(issue("119", "Bad Amount", "1,000.00", "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("0.00"));
        mvc.perform(issue("119", "Reserved Claimant", "-50", "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("-50.00"));
        mvc.perform(issue("119", "Reserved Claimant", "1.005", "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("1.01"));
        mvc.perform(issue("119", "Reserved Claimant", "1e3", "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        mvc.perform(issue("119", "Reserved Claimant", "NaN", "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("NaN"));
        assertThat(((Number) payment(64).get("AMOUNT")).doubleValue()).isEqualTo(1.005);
    }

    /**
     * PAY-R09 (v1), PAY-R10 (v1). live:issue-next-id#1, live:issue-check-number#1: the next
     * id is max(payment_id)+1 over the whole table (501 after a row 500) and the check
     * number is CHK-&lt;id&gt; whatever checkNumber is sent.
     */
    @Test
    void nextIdAndCheckNumber() throws Exception {
        jdbc.update("INSERT INTO PAYMENT VALUES (500, 1, 1, 'Gap Payee', 1, 'CHECK', 'CHK-GAP',"
                + " '2019-03-15', 'ISSUED')");
        mvc.perform(issue("119", "Reserved Claimant", "1000.00", "CHECK").param("checkNumber", "MINE"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-501"));
    }

    /**
     * PAY-R13 (v1), PAY-R14 (v1). live:issue-required-columns#1-#3,
     * live:issue-column-limits#1-#3: a missing payeeName or paymentMethod, a 21-character
     * method or a 101-character payee fail the insert (error, no row, id not used); 100
     * and 20 characters are stored.
     */
    @Test
    void columnConstraintsFailTheInsert() throws Exception {
        mvc.perform(issue("119", null, "10", "CHECK")).andExpect(status().is5xxServerError());
        mvc.perform(issue("119", "No Method", "10", null)).andExpect(status().is5xxServerError());
        mvc.perform(issue("119", "Reserved Claimant", "1000.00", "M".repeat(21)))
                .andExpect(status().is5xxServerError());
        mvc.perform(issue("119", "P".repeat(101), "1000.00", "CHECK"))
                .andExpect(status().is5xxServerError());
        mvc.perform(issue("119", "P".repeat(100), "1000.00", "M".repeat(20)))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"));
    }

    /**
     * PAY-R12 (v1), PAY-R15 (v1). live:issue-raw-text#1, live:issue-no-business-checks#1-#3:
     * payee and method stored as sent; a CLOSED claim, an already paid claim and an
     * amount far above the settlement are all accepted.
     */
    @Test
    void rawTextAndNoBusinessChecks() throws Exception {
        mvc.perform(issue("119", "<b>A&B</b> Payee", "1000.00", "wire")).andExpect(status().isOk());
        assertThat(payment(61).get("PAYEE_NAME")).isEqualTo("<b>A&B</b> Payee");
        mvc.perform(issue("120", "Reserved Claimant", "1000.00", "CHECK")).andExpect(status().isOk());
        mvc.perform(issue("1", "Reserved Claimant", "999999", "CHECK"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("999999.00"));
    }

    /**
     * PAY-R26 (v1). live:unauthenticated#1: without an operator the request is
     * redirected to /claims/login.do and nothing is written (AuthFilter.java:37-40).
     */
    @Test
    void noOperatorRedirectsToLoginAndWritesNothing() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "119").param("payeeName", "X")
                        .param("amount", "1").param("paymentMethod", "CHECK"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/claims/login.do"));
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(60);
    }
}
