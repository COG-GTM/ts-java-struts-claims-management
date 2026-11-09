package com.northstar.claims;

import com.northstar.claims.support.ClaimsSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:foundation")
@AutoConfigureMockMvc
class FoundationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ClaimsSupport support;

    @Test
    void authenticatesWithForwardAndPersistsSessionThenLogsOut() throws Exception {
        mvc.perform(get("/home.do")).andExpect(status().isFound())
                .andExpect(header().string("Location", "/login.do"));
        mvc.perform(get("/login.do")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Claims Login")));
        var result = mvc.perform(post("/login.do").param("username", "supervisor")
                .param("password", "supervisor")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/home.jsp -->")))
                .andExpect(content().string(containsString("<span id=\"f_signedInUser\">supervisor</span>")))
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession();
        assertEquals("supervisor", session.getAttribute("displayName"));
        mvc.perform(get("/home.do").session(session)).andExpect(status().isOk());
        mvc.perform(get("/logout.do").session(session)).andExpect(status().isOk())
                .andExpect(view().name("login"));
        assertTrue(session.isInvalid());
    }

    @Test
    void rejectsBadAndMissingCredentialsButRetainsPrefixRule() throws Exception {
        mvc.perform(post("/login.do")).andExpect(status().isOk()).andExpect(view().name("login"));
        mvc.perform(post("/login.do").param("username", "supervisor").param("password", "bad"))
                .andExpect(status().isOk()).andExpect(view().name("login"));
        mvc.perform(post("/login.do").param("username", "adjuster-anything").param("password", "legacy-anything"))
                .andExpect(status().isOk()).andExpect(view().name("home"));
    }

    @Test
    void sharedParsingRetainsFallbackAndLeniency() {
        assertEquals("2019-03-02", support.normalizedDate("02/30/2019"));
        assertEquals("2019-04-01", support.normalizedDate(""));
        assertEquals("2019-04-01", support.normalizedDate("not-a-date"));
        assertEquals(119, support.integer(null, 119));
        assertEquals(2.0, support.decimal("bad", 2.0));
        assertTrue(support.validDateShape("02/30/2019"));
        assertFalse(support.validDateShape("2019-02-30"));
    }
}
