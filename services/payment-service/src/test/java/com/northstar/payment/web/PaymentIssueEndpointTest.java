package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoint tests for /payment/issue against the seeded fixture plus the
 * settlement 121 (claim 119, 1000.00) that live L05 of
 * docs/specs/SPEC-PAY-001-live.json created before the issue requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:payment-issue-test")
class PaymentIssueEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void settlement121() {
        jdbc.update("delete from PAYMENT where payment_id > 60");
        jdbc.update("delete from SETTLEMENT where settlement_id = 121");
        jdbc.update("insert into SETTLEMENT values (121, 119, 5000, 500, 0, false, 1000,"
                + " 'supervisor', '2019-04-01')");
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from PAYMENT", Integer.class);
    }

    /**
     * Rules: PAY-R01, PAY-R03, PAY-R06, PAY-R07, PAY-R08, PAY-R09.
     * Transcript: payment_issue — checkNumber CHK-61, paymentAmount 1000.00,
     * paymentStatus ISSUED, db_state payment.61.amount 1000.00. The row links
     * to settlement 121, the newest for claim 119
     * (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-36).
     */
    @Test
    void issueInsertsRow61ForClaim119() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "119")
                        .param("payeeName", "Reserved Claimant").param("amount", "1000.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("issue"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"))
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.paymentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.fields.length()").value(3));
        Map<String, Object> row = jdbc.queryForMap("select * from PAYMENT where payment_id = 61");
        assertThat(row.get("SETTLEMENT_ID")).isEqualTo(121);
        assertThat(row.get("PAYEE_NAME")).isEqualTo("Reserved Claimant");
        assertThat(row.get("PAYMENT_METHOD")).isEqualTo("CHECK");
        assertThat(row.get("CHECK_NUMBER")).isEqualTo("CHK-61");
        assertThat(String.valueOf(row.get("ISSUED_DATE"))).isEqualTo("2019-04-03");
        assertThat(row.get("STATUS")).isEqualTo("ISSUED");
        assertThat(count()).isEqualTo(61);
    }

    /**
     * Rules: PAY-R02, PAY-R04. No claimId is claim 119 and no amount is the
     * newest settlement's amount, 1000.00 (live L06, L19;
     * ClaimsActionSupport.java:31-45; PaymentIssueAction.java:22,30-31).
     */
    @Test
    void missingClaimIdAndAmountFallBack() throws Exception {
        mvc.perform(post("/payment/issue").param("payeeName", "Reserved Claimant")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"));
        assertThat(jdbc.queryForObject("select claim_id from PAYMENT where payment_id = 61",
                Integer.class)).isEqualTo(119);
    }

    /**
     * Rule: PAY-R05. -250.00, 1.005, 5000.00, NaN and 1e3 are all accepted;
     * the row keeps the parsed double and the screen prints %.2f
     * (live L08 -250.00, L09 1.01, L10 5000.00, L11 NaN, L12 1000.00;
     * db_after.payment_rows_after_seed amount 1.005E0 for payment 65).
     */
    @Test
    void anyParseableDoubleIsAccepted() throws Exception {
        String[][] cases = {{"-250.00", "-250.00"}, {"1.005", "1.01"}, {"5000.00", "5000.00"},
                {"NaN", "NaN"}, {"1e3", "1000.00"}};
        for (String[] c : cases) {
            mvc.perform(post("/payment/issue").param("claimId", "119")
                            .param("payeeName", "Reserved Claimant").param("amount", c[0])
                            .param("paymentMethod", "CHECK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fields.paymentAmount").value(c[1]));
        }
        assertThat(jdbc.queryForObject("select amount from PAYMENT where payment_id = 62",
                Double.class)).isEqualTo(1.005);
        assertThat(jdbc.queryForObject("select amount from PAYMENT where payment_id = 64",
                Double.class)).isNaN();
    }

    /**
     * Rules: PAY-R04, PAY-R07, PAY-R09. amount=abc falls back to 1000.00
     * (live L07); checkNumber=MY-CHECK-1 is ignored and the check is CHK-61
     * (live L13); paymentMethod=check is stored lower-case (live L14).
     */
    @Test
    void unparseableAmountIgnoredCheckNumberAndRawMethod() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "119")
                        .param("payeeName", "Reserved Claimant").param("amount", "abc")
                        .param("checkNumber", "MY-CHECK-1").param("paymentMethod", "check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"));
        assertThat(jdbc.queryForObject("select payment_method from PAYMENT where payment_id = 61",
                String.class)).isEqualTo("check");
    }

    /**
     * Rules: PAY-R06, PAY-R10. A missing paymentMethod (live L15), a missing
     * payeeName (live L16) and a 21-character method (live L17) fail the
     * NOT NULL / VARCHAR(20) insert (src/main/resources/db/schema.sql:106-118):
     * error screen, no row, and the next successful issue still takes id 61.
     */
    @Test
    void insertFailuresWriteNothingAndConsumeNoId() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "119")
                        .param("payeeName", "Reserved Claimant").param("amount", "10.00"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"))
                .andExpect(jsonPath("$.fields.length()").value(0));
        mvc.perform(post("/payment/issue").param("claimId", "119").param("amount", "10.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError());
        mvc.perform(post("/payment/issue").param("claimId", "119")
                        .param("payeeName", "Reserved Claimant").param("amount", "10.00")
                        .param("paymentMethod", "ELECTRONIC-FUNDS-XFER"))
                .andExpect(status().is5xxServerError());
        assertThat(count()).isEqualTo(60);
        mvc.perform(post("/payment/issue").param("claimId", "119")
                        .param("payeeName", "Reserved Claimant").param("amount", "10.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-61"));
    }

    /**
     * Rule: PAY-R11. Claim 9999 has no SETTLEMENT row: error screen and no row
     * (live L18; PaymentIssueAction.java:23,28).
     */
    @Test
    void claimWithoutSettlementIsAnError() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "9999")
                        .param("payeeName", "Reserved Claimant").param("amount", "10.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.screen").value("error"));
        assertThat(count()).isEqualTo(60);
    }

    /**
     * Rule: PAY-R12. Issue is accepted over GET (live L21) and for the CLOSED
     * claim 120 whose newest settlement is 120 at 0 (live L22, L36;
     * src/main/webapp/WEB-INF/struts-config.xml:223-231).
     */
    @Test
    void getIssuesAndClosedClaimIsNotChecked() throws Exception {
        mvc.perform(get("/payment/issue").param("claimId", "119")
                        .param("payeeName", "Reserved Claimant").param("amount", "11.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.paymentAmount").value("11.00"));
        mvc.perform(post("/payment/issue").param("claimId", "120")
                        .param("payeeName", "Reserved Claimant").param("amount", "12.00")
                        .param("paymentMethod", "CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.checkNumber").value("CHK-62"));
        assertThat(jdbc.queryForObject("select settlement_id from PAYMENT where payment_id = 62",
                Integer.class)).isEqualTo(120);
    }
}
