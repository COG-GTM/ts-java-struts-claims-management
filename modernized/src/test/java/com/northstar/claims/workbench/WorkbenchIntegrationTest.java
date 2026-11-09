package com.northstar.claims.workbench;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:workbench")
@AutoConfigureMockMvc
@WithMockUser
class WorkbenchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() throws Exception {
        // Use the same script boundaries as the existing repository integration coverage.
        for (String file : new String[] {"schema.sql", "seed.sql"}) {
            StringBuilder sql = new StringBuilder();
            for (String line : Files.readAllLines(Path.of("../src/main/resources/db", file))) {
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
    void mutationsKeepSparseResponseAttributesAndPersist() throws Exception {
        mvc.perform(post("/workbench/assign.do").param("claimId", "119").param("adjuster", "adjuster3"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("<span id=\"f_assignedAdjuster\">adjuster3</span>")));
        assertEquals("adjuster3", jdbc.queryForObject("select assigned_adjuster from CLAIM where claim_id=119", String.class));
        mvc.perform(post("/workbench/status.do?status=CLOSED").param("claimId", "119"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("<span id=\"f_claimStatus\">CLOSED</span>")));
        assertEquals("CLOSED", jdbc.queryForObject("select status from CLAIM where claim_id=119", String.class));
        mvc.perform(post("/workbench/reserve.do").param("claimId", "119").param("reserveAmount", "12.345"))
                .andExpect(status().isOk()).andExpect(view().name("workbench/view"));
        assertEquals(12.345, jdbc.queryForObject("select reserve_amount from CLAIM where claim_id=119", Double.class), .001);
    }

    @Test
    void invalidAndEmptyInputsRetainDisabledValidationAndGlobalErrorForward() throws Exception {
        // There is no reachable validation-failure forward: validate=false bypasses amountDyn rules.
        mvc.perform(post("/workbench/reserve.do").param("reserveAmount", "invalid"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("<span id=\"f_reserveAmount\">4500.00</span>")));
        mvc.perform(post("/workbench/status.do").param("status", ""))
                .andExpect(status().isOk()).andExpect(content().string(containsString("INVESTIGATING")));
        mvc.perform(post("/workbench/assign.do").param("assignedAdjuster", "ignored"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("adjuster2")));
        mvc.perform(post("/workbench/status.do").param("status", "'"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
        mvc.perform(get("/workbench/view.do").param("claimId", "-1"))
                .andExpect(status().isOk()).andExpect(view().name("error"));
    }

    @Test
    void notesAreEphemeralAndHistoryPlaceholdersStayEmpty() throws Exception {
        int count = jdbc.queryForObject("select count(*) from CLAIM_NOTE", Integer.class);
        mvc.perform(post("/workbench/note.do").param("noteText", ""))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Review completed")));
        assertEquals(count, jdbc.queryForObject("select count(*) from CLAIM_NOTE", Integer.class));
        for (String page : new String[] {"list", "notes", "reserveHistory", "statusHistory"}) {
            mvc.perform(get("/workbench/" + page + ".do").param("claimId", "-1"))
                    .andExpect(status().isOk()).andExpect(view().name("workbench/" + page));
        }
    }
}
