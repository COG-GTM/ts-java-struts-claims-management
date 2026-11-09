package com.northstar.claims.settlement;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:settlement")
@AutoConfigureMockMvc
@WithMockUser
class SettlementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() throws Exception {
        // Match the foundation bootstrap's SQL boundaries against an isolated real JDBC store.
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
    void calculatesClosedClaimsWithBlankDeductibleRoundingCapAndFloor() throws Exception {
        jdbc.update("update CLAIM set status = 'CLOSED' where claim_id = 119");
        mvc.perform(post("/settlement/calculate.do").param("coveredAmount", "1.005")
                .param("deductible", ""))
                .andExpect(status().isOk()).andExpect(view().name("settlement/calculate"))
                .andExpect(content().string(containsString("<span id=\"f_settlementAmount\">1.00</span>")))
                .andExpect(content().string(containsString("<span id=\"f_deductibleApplied\">0.00</span>")));
        mvc.perform(get("/settlement/calculate.do").param("coveredAmount", "5000")
                .param("policyLimit", "99999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_cappedAtLimit\">true</span>")))
                .andExpect(content().string(containsString("<span id=\"f_settlementAmount\">1000.00</span>")));
        mvc.perform(get("/settlement/calculate.do").param("coveredAmount", "10").param("deductible", "20"))
                .andExpect(content().string(containsString("<span id=\"f_settlementAmount\">0.00</span>")));
    }

    @Test
    void savesDeterministicIdsAndLoadsLatestDetail() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute("user", "supervisor");
        int next = jdbc.queryForObject("select max(settlement_id)+1 from SETTLEMENT", Integer.class);
        mvc.perform(post("/settlement/save.do").session(session).param("coveredAmount", "900")
                .param("deductible", "").param("depreciation", "100"))
                .andExpect(status().isOk()).andExpect(view().name("settlement/save"))
                .andExpect(content().string(containsString("<span id=\"f_savedBy\">supervisor</span>")));
        assertEquals(800.0, jdbc.queryForObject("select settlement_amount from SETTLEMENT where settlement_id = ?",
                Double.class, next));
        assertEquals("2019-04-01", jdbc.queryForObject("select calculated_date from SETTLEMENT where settlement_id = ?",
                String.class, next));
        mvc.perform(get("/settlement/detail.do"))
                .andExpect(status().isOk()).andExpect(view().name("settlement/detail"))
                .andExpect(content().string(containsString("<span id=\"f_detailSettlementId\">" + next + "</span>")))
                .andExpect(content().string(containsString("<span id=\"f_detailAmount\">800.00</span>")));
    }

    @Test
    void preservesDefaultsEmptyResultsAndErrorForwards() throws Exception {
        mvc.perform(get("/settlement/calculate.do"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_coveredAmount\">5000.00</span>")));
        mvc.perform(get("/settlement/calculate.do").param("claimId", "-1").param("coveredAmount", "bad"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_settlementAmount\">5000.00</span>")));
        mvc.perform(post("/settlement/calculate.do").param("deductible", "bad"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
        mvc.perform(post("/settlement/save.do").param("claimId", "-1"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
        mvc.perform(get("/settlement/detail.do").param("claimId", "-1"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
    }
}
