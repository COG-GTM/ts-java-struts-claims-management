package com.northstar.claims;

import com.northstar.claims.repository.*;
import com.northstar.claims.service.SettlementService;
import com.northstar.claims.support.ClaimsSupport;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:repositories")
@AutoConfigureMockMvc
class RepositoryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ClaimDAO claims;
    @Autowired PolicyDAO policies;
    @Autowired AdjusterDAO adjusters;
    @Autowired NoteDAO notes;
    @Autowired PaymentDAO payments;
    @Autowired SettlementDAO settlements;
    @Autowired ReportDAO reports;
    @Autowired SettlementService settlementService;
    @Autowired ClaimsSupport support;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seedInMemoryFromLegacyScripts() throws Exception {
        // Keep the bootstrap's line concatenation and semicolon boundaries, not a new SQL parser.
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
    void allGatewaysReadSeedAndPreserveEmptyResults() throws Exception {
        for (Object repository : new Object[] {claims, policies, adjusters, notes, payments, settlements}) {
            Class<?> type = org.springframework.aop.support.AopUtils.getTargetClass(repository);
            int count = (Integer) type.getMethod("count").invoke(repository);
            var rows = (java.util.List<?>) type.getMethod("findAll").invoke(repository);
            assertEquals(count, rows.size());
            assertNull(type.getMethod("findById", int.class).invoke(repository, -1));
        }
        assertEquals(42, policies.count());
        assertEquals(1000, policies.findById(9001).getPolicyLimit());
        assertFalse(claims.findByStatus("OPEN").isEmpty());
        assertTrue(claims.findByStatus("MISSING").isEmpty());
        assertFalse(claims.search("", "OPEN").isEmpty());
        assertFalse(policies.search("").isEmpty());
        assertFalse(policies.findByLine("AUTO").isEmpty());
        assertFalse(adjusters.findActive().isEmpty());
        assertNull(adjusters.authenticate("absent", "bad"));
        assertTrue(notes.findByClaim(-1).isEmpty());
        assertNull(notes.findUnused(-1));
        assertFalse(payments.findByClaim(2).isEmpty());
        assertEquals(0, payments.totalIssued(-1));
        assertNull(settlements.findByClaim(-1));
        assertFalse(reports.openClaimsByAdjuster().isEmpty());
        assertFalse(reports.lossRatioByLine().isEmpty());
        assertFalse(reports.agedClaims().isEmpty());
        assertFalse(reports.claimCounts("status").isEmpty());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> reports.claimCounts("missing_column"));
    }

    @Test
    void writesAndSharedHelpersUseTheSameDatabase() throws Exception {
        claims.assign(119, "adjuster7");
        claims.updateReserve(119, 123.45);
        claims.updateStatus(119, "CLOSED");
        assertEquals("adjuster7", claims.findById(119).getAssignedAdjuster());
        assertEquals(123.45, claims.findById(119).getReserveAmount());
        assertEquals("CLOSED", claims.findById(119).getStatus());
        assertEquals(121, support.nextId("CLAIM"));
        var value = settlementService.calculateAndSave(119, 5000, "", 500, 1000);
        assertEquals(1000, settlementService.findByClaim(119).getSettlementAmount());
        settlements.delete(value.getSettlementId());
        assertNull(settlements.findById(value.getSettlementId()));
        policies.updateStatus(9001, "EXPIRED");
        assertEquals("EXPIRED", policies.findById(9001).getStatus());
        assertEquals("", support.selectString("select status from CLAIM where claim_id = ?", -1));
        assertEquals(0, support.selectAmount("select reserve_amount from CLAIM where claim_id = ?", -1));
    }

    @Test
    @WithMockUser
    void workbenchProbeRendersNormalMissingAndErrorStates() throws Exception {
        mvc.perform(get("/workbench/view.do").param("claimId", "119"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/workbench/view.jsp -->")))
                .andExpect(content().string(containsString("<span id=\"f_reserveAmount\">")))
                .andExpect(content().string(containsString("<span id=\"f_lossDate\">")));
        mvc.perform(get("/workbench/view.do").param("claimId", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/error.jsp -->")));
        mvc.perform(get("/not-implemented.do"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/error.jsp -->")))
                .andExpect(content().string(containsString("Claims System Error")));
    }
}
