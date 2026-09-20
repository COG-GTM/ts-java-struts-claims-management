package com.northstar.settlement.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.settlement.domain.CalculatedSettlement;
import com.northstar.settlement.domain.ClaimNotFoundException;
import com.northstar.settlement.domain.SettlementRequest;
import com.northstar.settlement.domain.SettlementResult;
import com.northstar.settlement.domain.SettlementService;
import com.northstar.settlement.persistence.ClaimRecord;
import com.northstar.settlement.persistence.SettlementRow;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the inbound/outbound contract of the three mappings: SETTLE-R01, R02, R03,
 * R04, R07, R08, R18, R34, R35 and R44.
 */
@WebMvcTest(SettlementController.class)
class SettlementControllerTest {

    private static final SettlementResult CAPPED =
            new SettlementResult(5000.0, 500.0, 0.0, true, 1000.0);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SettlementService service;

    @Test
    @DisplayName("SETTLE-R01 / SETTLE-R07 / SETTLE-R34 / SETTLE-R35: calculate forwards to calculate.jsp"
            + " with the five formatted fields")
    void settleR01R07R34R35CalculateScreen() throws Exception {
        when(service.calculate(any(SettlementRequest.class)))
                .thenReturn(new CalculatedSettlement(119, 1000.0, CAPPED));
        mvc.perform(post("/api/settlement/calculate")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("claimId", "119").param("coveredAmount", "5000.00")
                        .param("deductible", "500.00").param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacyForward").value("/WEB-INF/jsp/settlement/calculate.jsp"))
                .andExpect(jsonPath("$.fields.coveredAmount").value("5000.00"))
                .andExpect(jsonPath("$.fields.deductibleApplied").value("500.00"))
                .andExpect(jsonPath("$.fields.depreciation").value("0.00"))
                .andExpect(jsonPath("$.fields.cappedAtLimit").value("true"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("1000.00"))
                .andExpect(jsonPath("$.validationErrors").isEmpty())
                .andExpect(jsonPath("$.data.policyLimit").value(1000.0));
    }

    @Test
    @DisplayName("SETTLE-R04 / SETTLE-R05: a bare POST with no parameters is accepted without validation errors")
    void settleR04R05NoParametersNoValidationErrors() throws Exception {
        when(service.calculate(any(SettlementRequest.class)))
                .thenReturn(new CalculatedSettlement(119, 1000.0, CAPPED));
        mvc.perform(post("/api/settlement/calculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationErrors").isEmpty());
    }

    @Test
    @DisplayName("SETTLE-R02 / SETTLE-R08 / SETTLE-R20: save forwards to save.jsp with settlementAmount and savedBy")
    void settleR02R08R20SaveScreen() throws Exception {
        when(service.save(any(SettlementRequest.class))).thenReturn(new SettlementRow(
                121, 119, 5000.0, 500.0, 0.0, true, 1000.0, "supervisor", LocalDate.of(2019, 4, 1)));
        when(service.operator()).thenReturn("supervisor");
        mvc.perform(post("/api/settlement/save")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("claimId", "119").param("coveredAmount", "5000.00")
                        .param("deductible", "500.00").param("depreciation", "0.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacyForward").value("/WEB-INF/jsp/settlement/save.jsp"))
                .andExpect(jsonPath("$.fields.settlementAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.savedBy").value("supervisor"))
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.data.settlementId").value(121));
    }

    @Test
    @DisplayName("SETTLE-R18: legacy quirk - a non-numeric deductible returns HTTP 200 and the error.jsp forward")
    void settleR18NonNumericDeductibleIsErrorScreenWith200() throws Exception {
        when(service.calculate(any(SettlementRequest.class))).thenThrow(new NumberFormatException("abc"));
        mvc.perform(post("/api/settlement/calculate")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("claimId", "120").param("coveredAmount", "1000")
                        .param("deductible", "abc").param("depreciation", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacyForward").value("/WEB-INF/jsp/error.jsp"))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.data.errorKey").value("errors.system"));
    }

    @Test
    @DisplayName("SETTLE-R15: legacy quirk - save for an unknown claim returns HTTP 200 and the error.jsp forward")
    void settleR15SaveUnknownClaimIsErrorScreenWith200() throws Exception {
        when(service.save(any(SettlementRequest.class))).thenThrow(new ClaimNotFoundException(999));
        mvc.perform(post("/api/settlement/save").param("claimId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacyForward").value("/WEB-INF/jsp/error.jsp"));
    }

    @Test
    @DisplayName("SETTLE-R03 / SETTLE-R45: detail forwards to detail.jsp with the persisted row")
    void settleR03R45DetailScreen() throws Exception {
        when(service.detail(119)).thenReturn(Optional.of(new SettlementRow(
                121, 119, 5000.0, 500.0, 0.0, true, 1000.0, "supervisor", LocalDate.of(2019, 4, 1))));
        mvc.perform(get("/api/settlement/detail").param("claimId", "119"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacyForward").value("/WEB-INF/jsp/settlement/detail.jsp"))
                .andExpect(jsonPath("$.fields.detailSettlementId").value("121"))
                .andExpect(jsonPath("$.fields.detailAmount").value("1000.00"))
                .andExpect(jsonPath("$.fields.detailCapped").value("true"))
                .andExpect(jsonPath("$.fields.detailDate").value("2019-04-01"));
    }

    @Test
    @DisplayName("SETTLE-R11: detail without claimId looks up claim 119")
    void settleR11DetailDefaultsToClaim119() throws Exception {
        when(service.detail(119)).thenReturn(Optional.of(new SettlementRow(
                119, 119, 1500.0, 500.0, 100.0, false, 0.0, "adjuster1", LocalDate.of(2019, 3, 1))));
        mvc.perform(get("/api/settlement/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.detailClaimId").value("119"));
    }

    @Test
    @DisplayName("SETTLE-R46: deliberate change - detail with no saved settlement is 404 instead of a JSP failure")
    void settleR46DetailMissingSettlementIs404() throws Exception {
        when(service.detail(anyInt())).thenReturn(Optional.empty());
        mvc.perform(get("/api/settlement/detail").param("claimId", "5"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SETTLE-R44: the claim probe exposes the stored status")
    void settleR44ClaimProbe() throws Exception {
        when(service.claim(120)).thenReturn(Optional.of(new ClaimRecord(120, 9002, "CLOSED")));
        mvc.perform(get("/api/settlement/claims/120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.claimStatus").value("CLOSED"));
    }
}
