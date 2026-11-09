package com.northstar.claims.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises rendered markers against real HSQLDB queries, including legacy report quirks. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:reports")
@AutoConfigureMockMvc
class ReportsIntegrationTest {
    private static final String[] VIEWS = {"openByAdjuster", "lossRatio", "agedClaims",
            "adjusterWorkload", "claimAgingDetail", "reconciliation", "premiumDetail", "index"};
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() throws Exception {
        // Match the existing bootstrap's statement boundaries using the unchanged legacy scripts.
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
    void allReportsRequireAuthentication() throws Exception {
        for (String name : VIEWS) {
            mvc.perform(get("/report/" + name + ".do")).andExpect(status().isFound())
                    .andExpect(header().string("Location", "/login.do"));
        }
    }

    @Test
    @WithMockUser
    void allEightViewsForwardOnGetAndPostWithoutBindingOrValidation() throws Exception {
        for (String name : VIEWS) {
            String path = "/report/" + name + ".do";
            for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                    get(path), post(path)}) {
                mvc.perform(request.param("status", "MISSING").param("reportAsOf", "2099-01-01"))
                        .andExpect(status().isOk()).andExpect(view().name("reports/" + name))
                        .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/report/" + name + ".jsp -->")))
                        .andExpect(content().string(not(containsString("<!-- ns:error"))))
                        .andExpect(content().string(not(containsString("??"))));
            }
        }
    }

    @Test
    @WithMockUser
    void preservesDetailFieldsMissingMapValuesAndDuplicateIndexLinks() throws Exception {
        mvc.perform(get("/report/adjusterWorkload.do"))
                .andExpect(model().attribute("screenName", "report-detail"))
                .andExpect(model().attribute("screenMode", "read"))
                .andExpect(model().attribute("operatorScope", "claims"))
                .andExpect(content().string(containsString(field("workloadAsOf", "2019-04-01"))))
                .andExpect(content().string(containsString(field("workloadRows", "2"))))
                .andExpect(content().string(containsString(field("workloadOldest_adjuster1", ""))));
        mvc.perform(get("/report/reconciliation.do"))
                .andExpect(content().string(containsString(field("reconcileAsOf", "2019-04-01"))))
                .andExpect(content().string(containsString(field("reconcileRows", "4"))))
                .andExpect(content().string(containsString(field("reconcileReserve_AUTO", ""))))
                .andExpect(content().string(containsString(field("reconcilePaid_AUTO", ""))))
                .andExpect(content().string(containsString(field("reconcileVariance_AUTO", ""))));
        String aging = mvc.perform(get("/report/claimAgingDetail.do"))
                .andExpect(content().string(containsString(field("agingDate_1", "2019-03-15"))))
                .andExpect(content().string(containsString(field("agingReserve_1", "1001.00"))))
                .andExpect(content().string(containsString(field("agingDetailAsOf", "2019-04-01"))))
                .andExpect(content().string(not(containsString("f_agingClaim_2\""))))
                .andReturn().getResponse().getContentAsString();
        int open = jdbc.queryForObject("select count(*) from CLAIM where status='OPEN'", Integer.class);
        assertTrue(aging.contains(field("agingDetailCount", String.valueOf(open))));
        mvc.perform(get("/report/premiumDetail.do"))
                .andExpect(content().string(containsString(field("premiumRows", "42"))))
                .andExpect(content().string(containsString(field("premiumValue_1", "1200.00"))))
                .andExpect(content().string(containsString(field("premiumStatus_1", "ACTIVE"))))
                .andExpect(content().string(containsString(field("premiumAsOf", "2019-04-01"))));
        String index = mvc.perform(get("/report/index.do"))
                .andExpect(model().attribute("reportCount", 7))
                .andExpect(model().attribute("reportAccess", "supervisor"))
                .andExpect(model().attribute("reportAsOf", "2019-04-01"))
                .andReturn().getResponse().getContentAsString();
        for (String detail : new String[] {"adjusterWorkload", "claimAgingDetail", "reconciliation", "premiumDetail"}) {
            assertEquals(2, index.split("href=\"" + detail + ".do\"", -1).length - 1);
        }
    }

    @Test
    @WithMockUser
    void agingUsesInclusiveBoundariesFixedDateAndIntegerReserves() throws Exception {
        jdbc.update("update CLAIM set status='CLOSED'");
        int[] ages = {-1, 0, 30, 31, 60, 61, 90, 91};
        for (int i = 0; i < ages.length; i++) {
            insertClaim(1001 + i, 1, LocalDate.of(2019, 4, 1).minusDays(ages[i]).toString(),
                    i == 0 ? "APPROVED" : "OPEN", 10.75, "adjuster-boundary");
        }
        insertClaim(1010, 1, "2019-03-01", "DENIED", 999, "excluded");
        insertClaim(1011, 1, "2019-03-01", "CLOSED", 999, "excluded");
        String body = mvc.perform(get("/report/agedClaims.do").param("reportAsOf", "2099-01-01"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String[] buckets = {"0_30", "31_60", "61_90", "91_PLUS"};
        String[] counts = {"3", "2", "2", "1"};
        String[] reserves = {"32", "21", "21", "10"};
        int previous = -1;
        for (int i = 0; i < buckets.length; i++) {
            String marker = field("agedBucket_" + buckets[i], counts[i]);
            assertTrue(body.indexOf(marker) > previous);
            previous = body.indexOf(marker);
            assertTrue(body.contains(field("agedReserve_" + buckets[i], reserves[i])));
        }
    }

    @Test
    @WithMockUser
    void openReportKeepsStatusFilterAndAdjusterOrderingAndEscapesFields() throws Exception {
        jdbc.update("update CLAIM set status='CLOSED'");
        insertClaim(1001, 1, "2019-03-01", "INVESTIGATING", 10.25, "z-adjuster");
        insertClaim(1002, 1, "2019-03-01", "OPEN", 20.5, "a&<adjuster");
        insertClaim(1003, 1, "2019-03-01", "APPROVED", 999, "excluded");
        String body = mvc.perform(get("/report/openByAdjuster.do"))
                .andExpect(content().string(containsString(field("openCount_z-adjuster", "1"))))
                .andExpect(content().string(containsString(field("reserveTotal_z-adjuster", "10.25"))))
                .andExpect(content().string(containsString(field("reserveTotal_a&amp;&lt;adjuster", "20.50"))))
                .andExpect(content().string(not(containsString("f_openCount_excluded"))))
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.indexOf("f_openCount_a&amp;&lt;adjuster") < body.indexOf("f_openCount_z-adjuster"));
    }

    @Test
    @WithMockUser
    void lossRatioRetainsJoinMultiplicationAndAllPaymentStatuses() throws Exception {
        jdbc.update("insert into POLICY values (10001,'PARITY','PARITY_TEST','Test','Address',"
                + "'2018-01-01','2019-12-31',1000,0,100,'EXPIRED')");
        insertClaim(1001, 10001, "2019-03-01", "CLOSED", 10, "adjuster1");
        jdbc.update("insert into PAYMENT values (1001,1001,1,'Test',3,'CHECK','T1','2019-04-03','ISSUED')");
        jdbc.update("insert into PAYMENT values (1002,1001,1,'Test',4,'CHECK','T2','2019-04-03','VOIDED')");
        mvc.perform(get("/report/lossRatio.do"))
                .andExpect(content().string(containsString(field("premiumTotal_PARITY_TEST", "200.00"))))
                .andExpect(content().string(containsString(field("lossTotal_PARITY_TEST", "27.00"))))
                .andExpect(content().string(containsString(field("lossRatio_PARITY_TEST", "0.14"))));
        jdbc.update("update POLICY set annual_premium=0 where policy_id=10001");
        mvc.perform(get("/report/lossRatio.do"))
                .andExpect(content().string(containsString(field("lossRatio_PARITY_TEST", "0.00"))));
    }

    @Test
    @WithMockUser
    void emptyAggregateReportsStillRenderNormally() throws Exception {
        jdbc.update("update CLAIM set status='CLOSED'");
        for (String name : new String[] {"openByAdjuster", "agedClaims", "adjusterWorkload", "claimAgingDetail"}) {
            mvc.perform(get("/report/" + name + ".do")).andExpect(status().isOk())
                    .andExpect(view().name("reports/" + name));
        }
        mvc.perform(get("/report/adjusterWorkload.do"))
                .andExpect(content().string(containsString(field("workloadRows", "0"))));
        mvc.perform(get("/report/claimAgingDetail.do"))
                .andExpect(content().string(containsString(field("agingDetailCount", "0"))));
    }

    private void insertClaim(int id, int policy, String reported, String status, double reserve, String adjuster) {
        jdbc.update("insert into CLAIM values (?, ?, ?, 'Test', '2019-01-01', ?, 'FIRE', 'Test', ?, ?, ?,"
                + "'supervisor', '2019-04-01')", id, "TEST-" + id, policy, reported, status, reserve, adjuster);
    }

    private static String field(String name, String value) {
        return "<span id=\"f_" + name + "\">" + value + "</span>";
    }
}
