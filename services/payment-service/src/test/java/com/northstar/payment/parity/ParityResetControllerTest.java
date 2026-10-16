package com.northstar.payment.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The parity-profile fixture control (not a payment rule): a reset returns
 * the database to the legacy seed and applies the named precondition scripts
 * from parity/preconditions/payment in order; a name outside that directory
 * is refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("parity")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:hsqldb:mem:payment-parity-reset-test")
class ParityResetControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void resetRestoresTheSeedAndAppliesPreconditions() throws Exception {
        mvc.perform(post("/payment/issue").param("claimId", "118").param("payeeName", "X")
                        .param("amount", "1").param("paymentMethod", "CHECK"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(61);

        mvc.perform(post("/parity/reset")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)).isEqualTo(60);

        mvc.perform(post("/parity/reset").param("precondition", "capture-state.sql")
                        .param("precondition", "capture-state-payment-61.sql"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select max(settlement_id) from SETTLEMENT", Integer.class)).isEqualTo(121);
        mvc.perform(get("/payment/detail").param("paymentId", "61"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.detailPaymentAmount").value("1000.00"));
    }

    @Test
    void unknownOrEscapingPreconditionIsRefused() throws Exception {
        mvc.perform(post("/parity/reset").param("precondition", "../../../src/main/resources/db/seed.sql"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/parity/reset").param("precondition", "no-such-file.sql"))
                .andExpect(status().isBadRequest());
    }
}
