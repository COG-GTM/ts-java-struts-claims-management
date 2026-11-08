package com.northstar.claims.admin;

import com.northstar.claims.repository.AdjusterDAO;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real security, controller and template pipeline, including legacy failures. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:admin")
@AutoConfigureMockMvc
class AdminIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean AdjusterDAO adjusters;

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/login.do")
                .param("username", "adjuster1").param("password", "legacy1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    @Test
    void retainsModuleForwardFailuresAndIgnoresFormAndDispatchParameters() throws Exception {
        var session = login();
        when(adjusters.findAll()).thenReturn(List.of("unused row"));
        for (var request : List.of(get("/admin/adjusters.do"), post("/admin/adjusters.do")
                .param("fullName", "Changed").param("region", "West").param("active", "bad")
                .param("id", "1").param("method", "save").param("dispatch", "edit"))) {
            mvc.perform(request.session(session)).andExpect(status().isNotFound())
                    .andExpect(model().attribute("adjusterCount", 1))
                    .andExpect(content().string(containsString("<!-- ns:view /WEB-INF/jsp/error.jsp -->")))
                    .andExpect(content().string(not(containsString("id=\"f_"))))
                    .andExpect(content().string(not(containsString("<!-- ns:error"))));
        }
        verify(adjusters, times(2)).findAll();
        for (var request : List.of(get("/admin/reference.do"), post("/admin/reference.do"))) {
            mvc.perform(request.session(session)).andExpect(status().isNotFound())
                    .andExpect(model().attribute("referenceVersion", "2019.04"))
                    .andExpect(model().attribute("lossTypes", List.of("WATER", "FIRE", "THEFT", "COLLISION")))
                    .andExpect(view().name("admin/error"));
        }
        mvc.perform(get("/admin/editAdjuster.do").session(session))
                .andExpect(status().isInternalServerError()).andExpect(view().name("admin/error"))
                .andExpect(content().string(containsString("<span>500</span>")));
    }

    @Test
    void preservesEmptyQueryAndGlobalExceptionForward() throws Exception {
        var session = login();
        when(adjusters.findAll()).thenReturn(List.of());
        mvc.perform(get("/admin/adjusters.do").session(session)).andExpect(status().isNotFound())
                .andExpect(model().attribute("adjusterCount", 0));
        // DAO exceptions still use the shared Struts-style HTTP 200 error forward.
        when(adjusters.findAll()).thenThrow(new SQLException("unavailable"));
        mvc.perform(get("/admin/adjusters.do").session(session)).andExpect(status().isOk())
                .andExpect(view().name("error"));
    }

    @Test
    void requiresLoginButNotAnAdminRole() throws Exception {
        for (String path : List.of("adjusters", "reference", "editAdjuster")) {
            mvc.perform(get("/admin/" + path + ".do")).andExpect(status().isFound())
                    .andExpect(header().string("Location", "http://localhost/login.do"));
            mvc.perform(post("/admin/" + path + ".do")).andExpect(status().isFound())
                    .andExpect(header().string("Location", "http://localhost/login.do"));
        }
    }
}
