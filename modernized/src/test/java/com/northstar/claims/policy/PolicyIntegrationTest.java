package com.northstar.claims.policy;

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
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:policy")
@AutoConfigureMockMvc
@WithMockUser
class PolicyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedLegacyData() throws Exception {
        // Match the existing bootstrap's statement boundaries.
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
    void rendersEveryScreenAndKeepsReadsNonMutating() throws Exception {
        var before = jdbc.queryForList("select * from POLICY order by policy_id");
        for (String screen : new String[] {"search", "list", "view", "coverages", "coverageDetail", "insuredParty", "renewal"}) {
            mvc.perform(get("/policy/" + screen + ".do").param("policyId", "9001"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/policy/" + screen + ".jsp -->")));
        }
        mvc.perform(get("/policy/view.do").param("policyId", "9001"))
                .andExpect(content().string(containsString("<span id=\"f_policyLimit\">1000.00</span>")))
                .andExpect(content().string(containsString("<span id=\"f_deductibleApplied\">")));
        mvc.perform(get("/policy/list.do"))
                .andExpect(content().string(containsString("<span id=\"f_policyStatus_9001\">")));
        assertEquals(before, jdbc.queryForList("select * from POLICY order by policy_id"));
    }

    @Test
    void searchDefaultsOnlyNullAndEmptyAndForwardsFailuresWithoutValidation() throws Exception {
        int count = jdbc.queryForObject("select count(*) from POLICY where line_of_business = 'AUTO'", Integer.class);
        mvc.perform(get("/policy/search.do")).andExpect(model().attribute("searchLine", "AUTO"))
                .andExpect(model().attribute("searchCount", count));
        mvc.perform(post("/policy/search.do").param("lineOfBusiness", ""))
                .andExpect(status().isOk()).andExpect(model().attribute("searchCount", count));
        for (String line : new String[] {" ", "MISSING", "'"}) {
            mvc.perform(post("/policy/search.do").param("lineOfBusiness", line))
                    .andExpect(status().isOk()).andExpect(view().name("policy/search"))
                    .andExpect(model().attribute("searchLine", line))
                    .andExpect(model().attribute("searchCount", 0))
                    .andExpect(content().string(not(containsString("<!-- ns:error"))));
        }
    }

    @Test
    void preservesDifferentIdDefaultsAndMissingPolicyForwards() throws Exception {
        for (String screen : new String[] {"view", "coverages", "coverageDetail"}) {
            mvc.perform(get("/policy/" + screen + ".do").param("policyId", "-1"))
                    .andExpect(status().isOk()).andExpect(view().name("error"));
        }
        for (String value : new String[] {"", "bad"}) {
            mvc.perform(get("/policy/view.do").param("policyId", value))
                    .andExpect(model().attribute("policyId", 1));
            mvc.perform(get("/policy/coverageDetail.do").param("policyId", value))
                    .andExpect(content().string(containsString("<span id=\"f_coveragePolicyId\">9001</span>")));
        }
        mvc.perform(get("/policy/coverages.do")).andExpect(model().attribute("coverageCount", 0));
        mvc.perform(get("/policy/renewal.do").param("policyId", "-1"))
                .andExpect(content().string(containsString("<span id=\"f_renewalPolicyId\">9001</span>")));
        mvc.perform(get("/policy/insuredParty.do").param("policyId", "-1"))
                .andExpect(content().string(containsString("<span id=\"f_insuredPolicyId\">-1</span>")))
                .andExpect(content().string(containsString("Policy insured party")))
                .andExpect(content().string(containsString("<span id=\"f_insuredPolicyStatus\">ACTIVE</span>")));
        // Remove the fallback row without deleting unrelated seeded claim fixtures.
        jdbc.execute("set database referential integrity false");
        try {
            jdbc.update("delete from POLICY where policy_id = 9001");
            mvc.perform(get("/policy/renewal.do")).andExpect(status().isOk()).andExpect(view().name("error"));
        } finally {
            jdbc.execute("set database referential integrity true");
        }
    }

    @Test
    void retainsPerActionDatabaseFailureBehavior() throws Exception {
        jdbc.execute("alter table POLICY rename to POLICY_UNAVAILABLE");
        try {
            mvc.perform(get("/policy/list.do")).andExpect(status().isOk())
                    .andExpect(view().name("policy/list")).andExpect(model().attribute("policyCount", 0));
            mvc.perform(get("/policy/search.do")).andExpect(status().isOk())
                    .andExpect(view().name("policy/search")).andExpect(model().attribute("searchCount", 0));
            mvc.perform(get("/policy/insuredParty.do")).andExpect(status().isOk())
                    .andExpect(view().name("policy/insuredParty"));
            for (String screen : new String[] {"view", "coverages", "coverageDetail", "renewal"}) {
                mvc.perform(get("/policy/" + screen + ".do"))
                        .andExpect(status().isOk()).andExpect(view().name("error"));
            }
        } finally {
            jdbc.execute("alter table POLICY_UNAVAILABLE rename to POLICY");
        }
    }
}
