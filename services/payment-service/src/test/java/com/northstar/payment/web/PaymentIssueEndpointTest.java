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
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:payment-issue-test",
        // The state the legacy capture had reached before the payment transcripts
        // were recorded (parity/preconditions/payment.sql; CHG-002, "Parity effect").
        "spring.sql.init.data-locations=file:../../src/main/resources/db/seed.sql,"
                + "file:../../parity/preconditions/payment.sql"})
class PaymentIssueEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Rules: PAY-R01, PAY-R09, PAY-R10, PAY-R11, PAY-R12, PAY-R14, PAY-R15.
     * Transcript: payment_issue — 1000.00 for claim 119 becomes payment 61,
     * CHK-61, ISSUED, and db_state payment.61.amount 1000.00,
     * payment.count.claim.119 1. Runs first so the seed's 60 rows make the
     * new key 61 (src/main/resources/db/seed.sql:615-674).
     */
    @Test
    @Order(1)
    void issueInsertsPayment61ForClaim119() throws Exception {
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
     * No claimId and no amount: the claim is 119 and the amount is its latest
     * settlement's settlement_amount
     * (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-31).
     * With the capture's preconditions that settlement is 121 at 1000.00
     * (parity/preconditions/payment.sql), not seeded settlement 119 at 0
     * (src/main/resources/db/seed.sql:613). The new key is max+1 = 62.
     */
    @Test
    @Order(2)
    void issueDefaultsClaimAndAmountFromTheLatestSettlement() throws Exception {
        mvc.perform(post("/payment/issue")
                        .param("payeeName", "Reserved Claimant")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
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
     * Rules: PAY-R05 (v2).
     * Change: docs/changes/CHG-002-payment-exceeds-settlement.md — the legacy
     * action stores any parseable amount (v1 of the rule); the service rejects an
     * amount above the claim's latest settlement amount before the key
     * allocation and the insert. Claim 118's seeded settlement is 1118.00
     * (src/main/resources/db/seed.sql:612), so 1500.00 answers status 200, the
     * issue screen, no business fields and exactly one validation key,
     * payment.amount.exceedsSettlement; no PAYMENT row is written and the next
     * payment_id is not consumed.
     */
    @Test
    @Order(7)
    void overSettlementAmountIsAValidationErrorAndWritesNothing() throws Exception {
        int before = jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
        int nextBefore = jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Claimant 118")
                        .param("amount", "1500.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]").value("payment.amount.exceedsSettlement"));
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "select coalesce(max(payment_id),0)+1 from PAYMENT", Integer.class)).isEqualTo(nextBefore);
    }

    /**
     * Rules: PAY-R05 (v2).
     * Change: docs/changes/CHG-002-payment-exceeds-settlement.md — only an
     * amount strictly above the settlement amount is rejected: exactly the
     * settlement amount (1118.00 for claim 118) is still issued as before.
     */
    @Test
    @Order(8)
    void amountEqualToTheSettlementIsStillIssued() throws Exception {
        int before = jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "118")
                        .param("payeeName", "Claimant 118")
                        .param("amount", "1118.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1118.00"))
                .andExpect(jsonPath("$.fields.paymentStatus").value("ISSUED"));
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(before + 1);
    }

    /**
     * Rules: PAY-R04, PAY-R05 (v2).
     * Change: docs/changes/CHG-002-payment-exceeds-settlement.md — the cap
     * needs a settlement to compare against, so a claim with no settlement
     * still fails the request the legacy way (error screen, nothing written)
     * rather than answering the new validation key.
     */
    @Test
    @Order(9)
    void capDoesNotReplaceTheMissingSettlementFailure() throws Exception {
        int before = jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
        mvc.perform(post("/payment/issue")
                        .param("claimId", "9999")
                        .param("payeeName", "X")
                        .param("amount", "1500.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.errors").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(before);
    }
}
