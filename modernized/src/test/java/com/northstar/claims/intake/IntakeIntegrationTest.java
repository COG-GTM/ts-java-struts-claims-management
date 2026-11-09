package com.northstar.claims.intake;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real MVC rendering and SQL writes against the unchanged legacy schema/seed. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:intake")
@AutoConfigureMockMvc
@WithMockUser(username = "adjuster9")
class IntakeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @BeforeEach
    void seedLegacyDatabase() {
        new ResourceDatabasePopulator(
                new FileSystemResource("../src/main/resources/db/schema.sql"),
                new FileSystemResource("../src/main/resources/db/seed.sql")).execute(dataSource);
    }

    @Test
    void newScreenRetainsUnusedDefaultsAndRelativeActionWithEmptyInputs() throws Exception {
        mvc.perform(get("/intake/new.do"))
                .andExpect(status().isOk()).andExpect(view().name("intake/new"))
                .andExpect(model().attribute("intakeMode", "NEW"))
                .andExpect(model().attribute("defaultLossDate", "04/01/2019"))
                .andExpect(model().attribute("formTitle", "New first notice of loss"))
                .andExpect(model().attribute("screenName", "detail"))
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/intake/new.jsp -->")))
                .andExpect(content().string(containsString("action=\"intake/submit.do\"")))
                .andExpect(content().string(not(containsString("value=\"04/01/2019\""))))
                .andExpect(content().string(not(containsString("id=\"f_"))));
    }

    @Test
    void submissionPreservesIdsLenientDatesRawTextAndHardCodedColumns() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/intake/submit.do").session(session)
                .param("claimantName", "  Capture O'Claimant  ")
                .param("description", "  Water loss  ").param("lossDate", "04/01/2019")
                .param("lossType", "FIRE").param("policyId", "9002")
                .param("claimId", "999").param("status", "CLOSED"))
                .andExpect(status().isOk()).andExpect(view().name("intake/confirm"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/intake/confirm.jsp -->")))
                .andExpect(content().string(containsString("<span id=\"f_claimId\">121</span>")))
                .andExpect(content().string(containsString("<span id=\"f_claimStatus\">OPEN</span>")))
                .andExpect(content().string(not(containsString("<!-- ns:error"))));
        var row = jdbc.queryForMap("select * from CLAIM where claim_id=121");
        assertEquals("CLM-121", row.get("claim_number"));
        assertEquals(9001, row.get("policy_id"));
        assertEquals("  Capture O'Claimant  ", row.get("claimant_name"));
        assertEquals("  Water loss  ", row.get("description"));
        assertEquals("WATER", row.get("loss_type"));
        assertEquals("OPEN", row.get("status"));
        assertEquals(0.0, ((Number) row.get("reserve_amount")).doubleValue());
        assertEquals("adjuster1", row.get("assigned_adjuster"));
        assertEquals("supervisor", row.get("created_by"));
        assertEquals("2019-04-01", session.getAttribute("lastLossDate"));

        mvc.perform(post("/intake/submit.do").session(session)
                .param("claimantName", "Lenient Claimant").param("description", "Date parsing test")
                .param("lossDate", "02/30/2019"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_claimId\">122</span>")));
        for (String column : List.of("loss_date", "reported_date", "created_date")) {
            assertEquals("2019-04-01", row.get(column).toString());
            assertEquals("2019-03-02", jdbc.queryForObject(
                    "select " + column + " from CLAIM where claim_id=122", String.class));
        }
        assertEquals("2019-03-02", session.getAttribute("lastLossDate"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void absentOrBlankDateDefaultsAndGetSubmissionsRemainAccepted(String date) throws Exception {
        // Struts did not constrain the action's HTTP method or require lossDate.
        var request = get("/intake/submit.do").param("claimantName", "Default Date")
                .param("description", "Date omitted");
        if (date != null) request.param("lossDate", date);
        mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("intake/confirm"));
        assertEquals("2019-04-01", jdbc.queryForObject(
                "select loss_date from CLAIM where claim_id=121", String.class));
    }

    @Test
    void missingFieldsReturnOrderedErrorsWithoutWritesOrSessionChanges() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute("lastLossDate", "previous");
        var result = mvc.perform(post("/intake/submit.do").session(session).param("lossDate", "bad"))
                .andExpect(status().isOk()).andExpect(view().name("intake/new"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(model().attribute("nsValidationErrors", List.of(
                        "errors.claimant.required", "errors.description.required", "errors.lossdate.format")))
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/intake/new.jsp -->")))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int previous = -1;
        for (String key : List.of("errors.claimant.required", "errors.description.required", "errors.lossdate.format")) {
            int position = body.indexOf("<!-- ns:error " + key + " -->");
            assertTrue(position > previous);
            previous = position;
        }
        assertEquals("previous", session.getAttribute("lastLossDate"));
        assertEquals(120, jdbc.queryForObject("select max(claim_id) from CLAIM", Integer.class));
        assertFalse(body.contains("id=\"f_"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"claimantName", "description"})
    void whitespaceOnlyRequiredFieldIsRejectedAndInputsStayEmpty(String field) throws Exception {
        String key = field.equals("claimantName") ? "errors.claimant.required" : "errors.description.required";
        var request = post("/intake/submit.do")
                .param("claimantName", field.equals("claimantName") ? " \t " : "Claimant")
                .param("description", field.equals("description") ? " \t " : "Loss")
                .param("lossDate", "04/01/2019");
        mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("intake/new"))
                .andExpect(model().attribute("nsValidationErrors", List.of(key)))
                .andExpect(content().string(containsString("<!-- ns:error " + key + " -->")))
                .andExpect(content().string(not(containsString("value=\"Claimant\""))));
        assertEquals(120, jdbc.queryForObject("select max(claim_id) from CLAIM", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2019-02-30", "04/1/2019", " 04/01/2019", "04/01/2019 ", " "})
    void dateShapeValidationDoesNotTrimOrInsert(String date) throws Exception {
        mvc.perform(post("/intake/submit.do").param("claimantName", "Bad Date")
                .param("description", "Bad date validation").param("lossDate", date))
                .andExpect(status().isOk()).andExpect(view().name("intake/new"))
                .andExpect(model().attribute("nsValidationErrors", List.of("errors.lossdate.format")))
                .andExpect(content().string(containsString("<!-- ns:error errors.lossdate.format -->")));
        assertEquals(120, jdbc.queryForObject("select max(claim_id) from CLAIM", Integer.class));
    }

    @Test
    void directConfirmationEchoesIdWithoutLookupStatusOrSessionDate() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/intake/confirm.do").session(session).param("claimId", "00999")
                .param("claimStatus", "CLOSED"))
                .andExpect(status().isOk()).andExpect(view().name("intake/confirm"))
                .andExpect(model().attribute("confirmationMode", "READ_ONLY"))
                .andExpect(content().string(containsString("<span id=\"f_claimId\">999</span>")))
                .andExpect(content().string(containsString("<span id=\"f_claimStatus\"></span>")));
        mvc.perform(get("/intake/confirm.do").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span id=\"f_claimId\"></span>")));
        assertNull(session.getAttribute("lastLossDate"));
        assertEquals(120, jdbc.queryForObject("select max(claim_id) from CLAIM", Integer.class));
    }
}
