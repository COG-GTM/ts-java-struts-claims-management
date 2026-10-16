package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Endpoint tests for POST /payment/issue against the seeded fixture. */
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

    /** The SETTLEMENT row of parity/preconditions/payment/capture-state.sql. */
    static final String CAPTURE_STATE_SETTLEMENT =
            "INSERT INTO SETTLEMENT VALUES (121, 119, 5000, 500, 0, TRUE, 1000, "
                    + "'supervisor', '2019-04-01')";

    /**
     * Rules: PAY-R01, PAY-R09, PAY-R10, PAY-R11, PAY-R12, PAY-R14, PAY-R15.
     * Transcript: payment_issue — 1000.00 for claim 119 becomes payment 61,
     * CHK-61, ISSUED, and db_state payment.61.amount 1000.00,
     * payment.count.claim.119 1. Runs first so the seed's 60 rows make the
     * new key 61 (src/main/resources/db/seed.sql:615-674). Starts from the
     * capture state (parity/preconditions/payment/capture-state.sql): the
     * transcript was recorded after settlement_save wrote settlement 121 at
     * 1000.00, so the payment is not above it (PAY-R05 (v2)).
     */
    @Test
    @Order(1)
    void issueInsertsPayment61ForClaim119() throws Exception {
        jdbc.update(CAPTURE_STATE_SETTLEMENT);
        assertThat(jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class)).isEqualTo(61);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "119")
                        .param("payeeName", "Reserved Claimant")
                        .param("amount", "1000.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"))
                .andExpect(jsonPath("$.fields.paymentStatus").value("ISSUED"));
        Map<String, Object> row = jdbc.queryForMap("select * from PAYMENT where payment_id = 61");
        assertThat(row.get("CLAIM_ID")).isEqualTo(119);
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(121);
        assertThat(row.get("PAYEE_NAME")).isEqualTo("Reserved Claimant");
        assertThat(((Number) row.get("AMOUNT")).doubleValue()).isEqualTo(1000.00);
        assertThat(row.get("PAYMENT_METHOD")).isEqualTo("CHECK");
        assertThat(row.get("CHECK_NUMBER")).isEqualTo("CHK-61");
        assertThat(String.valueOf(row.get("ISSUED_DATE"))).isEqualTo("2019-04-03");
        assertThat(row.get("STATUS")).isEqualTo("ISSUED");
        assertThat(jdbc.queryForObject(
                "select count(*) from PAYMENT where claim_id = 119", Integer.class)).isEqualTo(1);
    }

    /**
     * Rules: PAY-R02, PAY-R03, PAY-R05.
     * No claimId and no amount: the claim is 119 and the amount is the
     * settlement_amount of claim 119's highest settlement_id, 121 at 1000.00
     * (inserted by the first test) rather than seeded 119 at 0
     * (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-31;
     * src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-114).
     * The fallback equals the settlement, so CHG-002 does not reject it. The
     * new key is max+1 = 62.
     */
    @Test
    @Order(2)
    void issueDefaultsClaimAndAmountFromTheLatestSettlement() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("payeeName", "Reserved Claimant")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-62"));
        Map<String, Object> row = jdbc.queryForMap("select * from PAYMENT where payment_id = 62");
        assertThat(row.get("CLAIM_ID")).isEqualTo(119);
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(121);
    }

    /**
     * Rules: PAY-R05, PAY-R06, PAY-R13.
     * An unparseable amount falls back to the settlement amount; payeeName and
     * paymentMethod are stored verbatim ("wire" stays lower case); CLAIM is not
     * updated (claim 118's status is unchanged).
     */
    @Test
    @Order(3)
    void issueStoresNameAndMethodVerbatimAndLeavesClaimAlone() throws Exception {
        String statusBefore = jdbc.queryForObject(
                "select status from CLAIM where claim_id = 118", String.class);
        double settlementAmount = jdbc.queryForObject(
                "select settlement_amount from SETTLEMENT where claim_id = 118 "
                        + "order by settlement_id desc limit 1", Double.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "  Mixed Case Payee ")
                        .param("amount", "ten dollars")
                        .param("paymentMethod", "wire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount")
                        .value(FieldFormatter.money(settlementAmount)));
        Map<String, Object> row = jdbc.queryForMap(
                "select * from PAYMENT where claim_id = 118 order by payment_id desc limit 1");
        assertThat(row.get("PAYEE_NAME")).isEqualTo("  Mixed Case Payee ");
        assertThat(row.get("PAYMENT_METHOD")).isEqualTo("wire");
        assertThat(((Number) row.get("AMOUNT")).doubleValue()).isEqualTo(settlementAmount);
        assertThat(jdbc.queryForObject(
                "select status from CLAIM where claim_id = 118", String.class)).isEqualTo(statusBefore);
    }

    /**
     * Rules: PAY-R07, PAY-R08.
     * A missing payeeName breaks the NOT NULL column: error screen, no fields,
     * no validation errors, no row written and the next id not consumed
     * (src/main/resources/db/schema.sql:110).
     */
    @Test
    @Order(4)
    void issueWithoutPayeeNameIsAnErrorScreenAndWritesNothing() throws Exception {
        int before = jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
        int nextBefore = jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("amount", "10")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class)).isEqualTo(nextBefore);
    }

    /**
     * Rules: PAY-R07.
     * A missing paymentMethod breaks the NOT NULL column the same way
     * (src/main/resources/db/schema.sql:112).
     */
    @Test
    @Order(5)
    void issueWithoutPaymentMethodIsAnErrorScreen() throws Exception {
        int before = jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Y")
                        .param("amount", "10"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(before);
    }

    /**
     * Rules: PAY-R04.
     * A claim with no settlement row fails before the insert: error screen and
     * nothing written (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,28).
     */
    @Test
    @Order(6)
    void issueForClaimWithoutSettlementIsAnErrorScreen() throws Exception {
        int before = jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "9999")
                        .param("payeeName", "X")
                        .param("amount", "10")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(before);
    }

    /**
     * Rules: PAY-R05 (v2), PAY-R13.
     * CHG-002: 1500.00 for claim 119, whose latest settlement is 121 at
     * 1000.00, is answered with status 200, the issue screen, no fields and
     * the one validation key payment.amount.exceedsSettlement. No PAYMENT row
     * is written and the next payment_id is not consumed. The legacy app
     * issues this payment (docs/changes/CHG-002-payment-above-settlement.md).
     */
    @Test
    @Order(7)
    void issueAboveTheLatestSettlementIsAValidationErrorAndWritesNothing() throws Exception {
        int before = jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
        int nextBefore = jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "119")
                        .param("payeeName", "Reserved Claimant")
                        .param("amount", "1500.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]").value(PaymentController.AMOUNT_EXCEEDS_SETTLEMENT));
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class)).isEqualTo(nextBefore);
    }

    /**
     * Rules: PAY-R05 (v2).
     * CHG-002 compares the parsed amount, unrounded, with the settlement
     * amount: 1000.001 is above 1000.00 and is rejected, although it would
     * print as 1000.00 (PAY-R27).
     */
    @Test
    @Order(8)
    void anyFractionAboveTheSettlementIsRejected() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("claimId", "119")
                        .param("payeeName", "Reserved Claimant")
                        .param("amount", "1000.001")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.errors[0]").value(PaymentController.AMOUNT_EXCEEDS_SETTLEMENT));
    }

    /**
     * Rules: PAY-R05 (v2).
     * Against seeded claim 118 (latest settlement 118 at 1118.00): an amount
     * below the settlement is issued unchanged, an amount equal to it is
     * issued, one cent above is rejected.
     */
    @Test
    @Order(9)
    void amountsUpToTheSettlementAreIssued() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Below")
                        .param("amount", "500")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("500.00"));
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Equal")
                        .param("amount", "1118.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1118.00"));
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Above")
                        .param("amount", "1118.01")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors[0]").value(PaymentController.AMOUNT_EXCEEDS_SETTLEMENT));
    }

    /**
     * Rules: PAY-R04, PAY-R05 (v2).
     * A claim without a settlement still fails as PAY-R04 says: the check of
     * CHG-002 runs after the settlement lookup, so there is no settlement to
     * compare against and the answer is the error screen, not a validation
     * error.
     */
    @Test
    @Order(10)
    void claimWithoutSettlementStillFailsBeforeTheCheck() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("claimId", "9999")
                        .param("payeeName", "X")
                        .param("amount", "1000000")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }
}
