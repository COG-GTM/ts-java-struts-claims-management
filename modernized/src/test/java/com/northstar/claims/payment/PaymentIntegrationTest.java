package com.northstar.claims.payment;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:payment")
@AutoConfigureMockMvc
@WithMockUser
class PaymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() throws Exception {
        // Use the same statement boundaries as the existing repository integration tests.
        for (String script : new String[] {"schema.sql", "seed.sql"}) {
            StringBuilder sql = new StringBuilder();
            for (String line : Files.readAllLines(Path.of("../src/main/resources/db", script))) {
                if (line.trim().isEmpty() || line.trim().startsWith("--")) continue;
                sql.append(line);
                if (line.trim().endsWith(";")) {
                    jdbc.update(sql.substring(0, sql.length() - 1));
                    sql.setLength(0);
                }
            }
        }
    }

    @Test
    void issuesAndReadsPersistedPaymentAcrossAllFourViews() throws Exception {
        mvc.perform(post("/payment/issue.do").param("claimId", "119")
                .param("payeeName", "Reserved Claimant").param("amount", "1000.00")
                .param("paymentMethod", "CHECK").param("checkNumber", "ignored"))
                .andExpect(status().isOk()).andExpect(view().name("payment/issue"))
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/payment/issue.jsp -->")))
                .andExpect(content().string(containsString("<span id=\"f_paymentAmount\">1000.00</span>")))
                .andExpect(content().string(containsString("<span id=\"f_checkNumber\">CHK-61</span>")));
        assertEquals("2019-04-03", jdbc.queryForObject(
                "select issued_date from PAYMENT where payment_id=61", String.class));
        mvc.perform(get("/payment/history.do")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_paymentAmount_61\">1000.00</span>")))
                .andExpect(content().string(containsString("<span id=\"f_paymentStatus_61\">ISSUED</span>")));
        mvc.perform(get("/payment/detail.do")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_detailPaymentDate\">2019-04-03</span>")));
        // The total includes non-issued rows, as the original SQL does.
        jdbc.update("update PAYMENT set status='VOID' where payment_id=61");
        mvc.perform(get("/payment/remittance.do")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_remitTotal\">1000.00</span>")));
    }

    @Test
    void preservesEmptyListsAndMissingResourceErrorForwards() throws Exception {
        mvc.perform(get("/payment/history.do").param("claimId", "-1"))
                .andExpect(status().isOk()).andExpect(view().name("payment/history"))
                .andExpect(content().string(not(containsString("f_paymentAmount_"))));
        mvc.perform(get("/payment/remittance.do").param("claimId", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_remitCount\">0</span>")))
                .andExpect(content().string(containsString("<span id=\"f_remitTotal\">0.00</span>")));
        mvc.perform(get("/payment/detail.do").param("paymentId", "-1"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
        mvc.perform(post("/payment/issue.do").param("claimId", "-1"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
        // No form validation runs; null payee/method fail at the database instead.
        mvc.perform(post("/payment/issue.do"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
        assertEquals(60, jdbc.queryForObject("select count(*) from PAYMENT", Integer.class));
    }

    @Test
    void defaultsAndUnvalidatedAmountsRetainLegacyBehavior() throws Exception {
        double settlement = jdbc.queryForObject(
                "select settlement_amount from SETTLEMENT where claim_id=119 order by settlement_id desc limit 1",
                Double.class);
        mvc.perform(get("/payment/issue.do").param("claimId", "bad").param("amount", "bad")
                .param("payeeName", "").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk()).andExpect(view().name("payment/issue"))
                .andExpect(content().string(not(containsString("ns:error"))));
        assertEquals(settlement, jdbc.queryForObject("select amount from PAYMENT where payment_id=61", Double.class));
        mvc.perform(post("/payment/issue.do").param("amount", "-1.005")
                .param("payeeName", "Payee").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk()).andExpect(view().name("payment/issue"));
        assertEquals(-1.005, jdbc.queryForObject("select amount from PAYMENT where payment_id=62", Double.class));
        mvc.perform(get("/payment/detail.do").param("paymentId", "bad"))
                .andExpect(status().isOk()).andExpect(view().name("payment/detail"));
    }
}
