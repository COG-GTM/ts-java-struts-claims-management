package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoint tests for /payment/issue against the seeded fixture (payments 1-60,
 * settlements 118/119/120 with amounts 1118/0/0) plus, from the first test on,
 * the state the capture had when transcripts/payment_issue.json was recorded:
 * settlement 121 for claim 119 at 1000.00
 * (parity/preconditions/payment/capture-state.sql). Tests run in order because
 * every issue allocates the next payment_id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:payment-issue-test")
class PaymentIssueEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private int paymentCount() {
        return jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
    }

    /**
     * Rules: PAY-R02, PAY-R14, PAY-R15, PAY-R16, PAY-R17 (v1 of each).
     * Transcript: payment_issue — 1000.00 for claim 119 becomes payment 61,
     * CHK-61, ISSUED, and the row carries claim 119, settlement 121 (the one
     * settlement_save wrote before the capture issued; capture-state.sql), issued
     * 2019-04-03 (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-43).
     */
    @Test
    @Order(1)
    void issueWritesPayment61() throws Exception {
        // parity/preconditions/payment/capture-state.sql: claim 119's newest settlement is 1000.00
        jdbc.update("insert into SETTLEMENT values (121, 119, 5000, 500, 0, TRUE, 1000, 'supervisor', '2019-04-01')");
        mvc.perform(post("/payment/issue")
                        .param("claimId", "119")
                        .param("payeeName", "Reserved Claimant")
                        .param("amount", "1000.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.length()").value(3))
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"))
                .andExpect(jsonPath("$.fields.paymentStatus").value("ISSUED"));
        Map<String, Object> row = jdbc.queryForMap("select * from PAYMENT where payment_id = 61");
        assertThat(row.get("CLAIM_ID")).isEqualTo(119);
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(121);
        assertThat(row.get("PAYEE_NAME")).isEqualTo("Reserved Claimant");
        assertThat(row.get("AMOUNT")).isEqualTo(1000.0);
        assertThat(row.get("PAYMENT_METHOD")).isEqualTo("CHECK");
        assertThat(row.get("CHECK_NUMBER")).isEqualTo("CHK-61");
        assertThat(row.get("ISSUED_DATE").toString()).isEqualTo("2019-04-03");
        assertThat(row.get("STATUS")).isEqualTo("ISSUED");
        assertThat(paymentCount()).isEqualTo(61);
    }

    /**
     * Rules: PAY-R03 (v1).
     * The same parameters by GET issue payment 62 exactly as a POST would
     * (SPEC-PAY-001 live step L26; src/main/webapp/WEB-INF/struts-config.xml:223-231).
     */
    @Test
    @Order(2)
    void getIssuesAPaymentToo() throws Exception {
        mvc.perform(get("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Get Payee")
                        .param("amount", "7")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("7.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-62"));
        assertThat(paymentCount()).isEqualTo(62);
    }

    /**
     * Rules: PAY-R04, PAY-R09 (v1 of each).
     * claimId "abc" is claim 119, and 12.345 is stored unrounded in the DOUBLE
     * column while the screen prints 12.35 (SPEC-PAY-001 live step L16, db row 63).
     */
    @Test
    @Order(3)
    void unparseableClaimIdIs119AndAmountIsStoredUnrounded() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("claimId", "abc")
                        .param("payeeName", "Third Payee")
                        .param("amount", "12.345")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("12.35"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-63"));
        Map<String, Object> row = jdbc.queryForMap("select claim_id, amount from PAYMENT where payment_id = 63");
        assertThat(row.get("CLAIM_ID")).isEqualTo(119);
        assertThat(row.get("AMOUNT")).isEqualTo(12.345);
    }

    /**
     * Rules: PAY-R06 (v1).
     * A missing amount is the newest settlement's amount: 1118.00 for claim
     * 118, 1118.00 again for "abc", 0.00 for claim 120 whose settlement amount
     * is 0 (SPEC-PAY-001 live steps L14, L15, L17).
     */
    @Test
    @Order(4)
    void missingOrUnparseableAmountFallsBackToTheSettlementAmount() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Live Payee")
                        .param("paymentMethod", "wire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1118.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-64"));
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Second Payee")
                        .param("amount", "abc")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1118.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-65"));
        mvc.perform(post("/payment/issue")
                        .param("claimId", "120")
                        .param("payeeName", "Zero Payee")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("0.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-66"));
    }

    /**
     * Rules: PAY-R07, PAY-R08 (v1 of each).
     * -5, 1e3 and " 5 " are accepted as -5.00, 1000.00 and 5.00; NaN is
     * accepted, stored as NaN, and shown as NaN on the detail screen and in the
     * remittance total (SPEC-PAY-001 live steps L25, L27, L29, L32, L45, L47).
     */
    @Test
    @Order(5)
    void anyParseableDoubleIsAccepted() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "Negative Payee")
                        .param("amount", "-5").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("-5.00"));
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "Sci Payee")
                        .param("amount", "1e3").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "Space Payee")
                        .param("amount", " 5 ").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("5.00"));
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "NaN Payee")
                        .param("amount", "NaN").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("NaN"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-70"));
        assertThat(jdbc.queryForObject("select amount from PAYMENT where payment_id = 70", Double.class)).isNaN();
        mvc.perform(get("/payment/detail").param("paymentId", "70"))
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("NaN"));
        mvc.perform(get("/payment/remittance").param("claimId", "118"))
                .andExpect(jsonPath("$.fields.remitTotal").value("NaN"));
    }

    /**
     * Rules: PAY-R10, PAY-R11 (v1 of each).
     * payeeName and paymentMethod are stored exactly as sent (case,
     * punctuation, HTML characters, the empty string); a checkNumber parameter
     * is ignored and the generated CHK-<id> is used (SPEC-PAY-001 live steps
     * L23, L30, L31, db rows 65, 71, 72).
     */
    @Test
    @Order(6)
    void payeeAndMethodAreStoredVerbatimAndCheckNumberIsIgnored() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "O'Brien <b>&</b> \"Co\"")
                        .param("amount", "2").param("paymentMethod", "ach").param("checkNumber", "MINE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-71"));
        Map<String, Object> row = jdbc.queryForMap("select payee_name, payment_method from PAYMENT where payment_id = 71");
        assertThat(row.get("PAYEE_NAME")).isEqualTo("O'Brien <b>&</b> \"Co\"");
        assertThat(row.get("PAYMENT_METHOD")).isEqualTo("ach");
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "")
                        .param("amount", "1").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-72"));
        assertThat(jdbc.queryForObject("select payee_name from PAYMENT where payment_id = 72", String.class)).isEmpty();
    }

    /**
     * Rules: PAY-R12, PAY-R13, PAY-R29 (v1 of each).
     * No payeeName, no paymentMethod, a 101-character payee and a 21-character
     * method each end on the error screen (5xx, no fields, no validation
     * errors) because only the schema's NOT NULL / VARCHAR constraints check
     * the input; nothing is written and the next id is unchanged, while 100
     * and 20 characters are accepted (SPEC-PAY-001 live steps L19-L22, L30;
     * src/main/resources/db/schema.sql:110,112).
     */
    @Test
    @Order(7)
    void schemaConstraintsAreTheOnlyInputCheckAndFailuresConsumeNoId() throws Exception {
        int before = paymentCount();
        int nextBefore = jdbc.queryForObject("select max(payment_id)+1 from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue").param("claimId", "118").param("amount", "1").param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "No Method").param("amount", "1"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "P".repeat(101))
                        .param("amount", "1").param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "Long Method")
                        .param("amount", "1").param("paymentMethod", "M".repeat(21)))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        assertThat(paymentCount()).isEqualTo(before);
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "Q".repeat(100))
                        .param("amount", "1").param("paymentMethod", "N".repeat(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-" + nextBefore));
    }

    /**
     * Rules: PAY-R05, PAY-R13, PAY-R29 (v1 of each).
     * A claim with no SETTLEMENT row fails before the insert (error screen,
     * nothing written); once a second settlement exists for claim 118 the
     * payment carries the newest settlement's id and amount, 2750.00 from
     * settlement 121 rather than 1118 from settlement 118 (SPEC-PAY-001 live
     * steps L18, L42, L43, db row 74;
     * src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-115).
     */
    @Test
    @Order(8)
    void paymentFollowsTheNewestSettlementAndFailsWithoutOne() throws Exception {
        int before = paymentCount();
        mvc.perform(post("/payment/issue").param("claimId", "9999").param("payeeName", "Nobody")
                        .param("amount", "1").param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
        assertThat(paymentCount()).isEqualTo(before);
        jdbc.update("insert into SETTLEMENT values (122, 118, 3000, 250, 0, FALSE, 2750, 'supervisor', '2019-04-01')");
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "Latest Payee")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("2750.00"));
        Map<String, Object> row = jdbc.queryForMap(
                "select settlement_id, amount from PAYMENT where payment_id = (select max(payment_id) from PAYMENT)");
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(122);
        assertThat(row.get("AMOUNT")).isEqualTo(2750.0);
    }

    /**
     * Rule: PAY-R07 (v2), approved by docs/changes/CHG-002-payment-exceeds-settlement.md.
     * Precondition as parity/preconditions/payment/chg-002-settlement-1000.sql:
     * claim 119's newest settlement is 121 at 1000.00 (inserted by the first test,
     * the same row), not the seeded 0.00. Legacy "Before" (CHG-002 record): 1500.00 is issued
     * as payment 61. Service: 1500.00 and 1000.01 are rejected with status 200,
     * the issue screen, no fields and exactly one error
     * {@code payment.amount.exceedsSettlement}; no row is written and the next
     * payment_id is not consumed. 1000.00 (equal), an omitted amount (PAY-R06
     * fallback) and NaN (PAY-R08) are still issued.
     */
    @Test
    @Order(9)
    void amountAboveTheNewestSettlementIsRejectedWithoutConsumingAnId() throws Exception {
        assertThat(jdbc.queryForObject(
                "select settlement_amount from SETTLEMENT where claim_id = 119 order by settlement_id desc limit 1",
                Double.class)).isEqualTo(1000.0);
        int before = paymentCount();
        int maxBefore = jdbc.queryForObject("select max(payment_id) from PAYMENT", Integer.class);
        for (String over : new String[] {"1500.00", "1000.01"}) {
            mvc.perform(post("/payment/issue").param("claimId", "119").param("payeeName", "Reserved Claimant")
                            .param("amount", over).param("paymentMethod", "CHECK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.screen").value("issue"))
                    .andExpect(jsonPath("$.fields").isEmpty())
                    .andExpect(jsonPath("$.errors[0]").value("payment.amount.exceedsSettlement"))
                    .andExpect(jsonPath("$.errors.length()").value(1));
        }
        assertThat(paymentCount()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select max(payment_id) from PAYMENT", Integer.class)).isEqualTo(maxBefore);
        mvc.perform(post("/payment/issue").param("claimId", "119").param("payeeName", "Reserved Claimant")
                        .param("amount", "1000.00").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-" + (maxBefore + 1)));
        mvc.perform(post("/payment/issue").param("claimId", "119").param("payeeName", "Reserved Claimant")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        mvc.perform(post("/payment/issue").param("claimId", "119").param("payeeName", "Reserved Claimant")
                        .param("amount", "NaN").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("NaN"));
        assertThat(paymentCount()).isEqualTo(before + 3);
    }
}
