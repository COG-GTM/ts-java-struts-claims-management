package com.northstar.settlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.settlement.calc.Settlement;
import com.northstar.settlement.calc.SettlementCalculator;
import com.northstar.settlement.persistence.ClaimRepository;
import com.northstar.settlement.persistence.PolicyRepository;
import com.northstar.settlement.persistence.SettlementRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Route-level cover of SPEC-SETTLE-001 sections 3.1, 3.2 and 3.5, asserted through the same
 * markers the transcript harness reads.
 */
class SettlementControllerTest {

    private static final Pattern FIELD = Pattern.compile("<span id=\"f_([^\"]+)\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern VIEW = Pattern.compile("<!--\\s*ns:view\\s+([^ ]+)\\s*-->");
    private static final Pattern ERROR = Pattern.compile("<!--\\s*ns:error\\s+([^ ]+)\\s*-->");

    private ClaimRepository claims;
    private PolicyRepository policies;
    private SettlementRepository settlements;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        claims = mock(ClaimRepository.class);
        policies = mock(PolicyRepository.class);
        settlements = mock(SettlementRepository.class);
        when(claims.findPolicyId(119)).thenReturn(9001);
        when(claims.findPolicyId(120)).thenReturn(9002);
        when(policies.findPolicyLimit(9001)).thenReturn(1000.00);
        when(policies.findPolicyLimit(9002)).thenReturn(100000.00);
        when(settlements.nextId()).thenReturn(121);
        mvc = MockMvcBuilders
                .standaloneSetup(new SettlementController(new SettlementCalculator(), claims, policies, settlements))
                .setControllerAdvice(new LegacyFailureAdvice())
                .build();
    }

    /**
     * SETTLE-R01, SETTLE-R07, SETTLE-R14, SETTLE-R24, SETTLE-R34: the
     * {@code settlement_calculate} transcript, capped at the policy limit of claim 119.
     */
    @Test
    void settleR01CalculateForwardsToTheCalculateScreenWithTheFiveFields() throws Exception {
        MvcResult result = mvc.perform(post("/claims/settlement/calculate.do")
                        .param("claimId", "119").param("coveredAmount", "5000.00")
                        .param("deductible", "500.00").param("depreciation", "0.00"))
                .andExpect(status().isOk()).andReturn();

        String body = body(result);
        assertThat(view(body)).isEqualTo("/WEB-INF/jsp/settlement/calculate.jsp");
        assertThat(fields(body)).containsExactly(
                Map.entry("coveredAmount", "5000.00"),
                Map.entry("deductibleApplied", "500.00"),
                Map.entry("depreciation", "0.00"),
                Map.entry("cappedAtLimit", "true"),
                Map.entry("settlementAmount", "1000.00"));
        assertThat(errors(body)).isEmpty();
    }

    /** SETTLE-R16, SETTLE-R26, SETTLE-R44: the {@code settlement_blank_deductible} transcript. */
    @Test
    void settleR16BlankDeductibleCalculatesWithZero() throws Exception {
        String body = body(mvc.perform(post("/claims/settlement/calculate.do")
                .param("claimId", "120").param("coveredAmount", "5000.00")
                .param("deductible", "").param("depreciation", "500.00")).andReturn());

        assertThat(fields(body)).containsEntry("deductibleApplied", "0.00")
                .containsEntry("cappedAtLimit", "false")
                .containsEntry("settlementAmount", "4500.00");
    }

    /** SETTLE-R28, SETTLE-R29, SETTLE-R34: the {@code settlement_half_cent} transcript. */
    @Test
    void settleR29HalfCentDisplaysOneCentMoreThanItSettles() throws Exception {
        String body = body(mvc.perform(post("/claims/settlement/calculate.do")
                .param("claimId", "120").param("coveredAmount", "1.005")
                .param("deductible", "").param("depreciation", "0.00")).andReturn());

        assertThat(fields(body)).containsEntry("coveredAmount", "1.01")
                .containsEntry("settlementAmount", "1.00");
    }

    /** SETTLE-R23: the {@code settlement_deductible_floor} transcript settles at 0.00. */
    @Test
    void settleR23DeductibleAboveLossSettlesAtZero() throws Exception {
        String body = body(mvc.perform(post("/claims/settlement/calculate.do")
                .param("claimId", "120").param("coveredAmount", "1000.00")
                .param("deductible", "2000.00").param("depreciation", "0.00")).andReturn());

        assertThat(fields(body)).containsEntry("deductibleApplied", "2000.00")
                .containsEntry("settlementAmount", "0.00");
    }

    /** SETTLE-R11, SETTLE-R12, SETTLE-R13: absent inputs fall back to claim 119 and 5000.00. */
    @Test
    void settleR11R12R13AbsentInputsFallBackSilently() throws Exception {
        String body = body(mvc.perform(post("/claims/settlement/calculate.do")).andReturn());

        assertThat(fields(body)).containsEntry("coveredAmount", "5000.00")
                .containsEntry("depreciation", "0.00")
                .containsEntry("settlementAmount", "1000.00");
    }

    /** SETTLE-R49: an unknown claim calculates against the 10000.00 fallback limit. */
    @Test
    void settleR49UnknownClaimUsesTheFallbackLimit() throws Exception {
        when(claims.findPolicyId(999999)).thenReturn(null);

        String body = body(mvc.perform(post("/claims/settlement/calculate.do")
                .param("claimId", "999999").param("coveredAmount", "20000.00")
                .param("deductible", "0").param("depreciation", "0")).andReturn());

        assertThat(fields(body)).containsEntry("settlementAmount", "10000.00")
                .containsEntry("cappedAtLimit", "true");
    }

    /**
     * SETTLE-R18 v2 (CHG-001): an invalid non-blank deductible redisplays the calculate screen
     * with HTTP 200, the {@code settlement.deductible.invalid} key and no settlement.
     */
    @Test
    void settleR18v2InvalidDeductibleRedisplaysTheCalculateScreen() throws Exception {
        MvcResult result = mvc.perform(post("/claims/settlement/calculate.do")
                        .param("claimId", "120").param("coveredAmount", "1000")
                        .param("deductible", "abc").param("depreciation", "0"))
                .andExpect(status().isOk()).andReturn();

        String body = body(result);
        assertThat(view(body)).isEqualTo("/WEB-INF/jsp/settlement/calculate.jsp");
        assertThat(errors(body)).containsExactly("settlement.deductible.invalid");
        assertThat(fields(body)).containsEntry("settlementAmount", "");
    }

    /** SETTLE-R02, SETTLE-R20, SETTLE-R39, SETTLE-R40, SETTLE-R41: the {@code settlement_save} transcript. */
    @Test
    void settleR02SaveInsertsAndRendersTheAmountAndOperator() throws Exception {
        MvcResult result = mvc.perform(post("/claims/settlement/save.do")
                        .param("claimId", "119").param("coveredAmount", "5000.00")
                        .param("deductible", "500.00").param("depreciation", "0.00"))
                .andExpect(status().isOk()).andReturn();

        String body = body(result);
        assertThat(view(body)).isEqualTo("/WEB-INF/jsp/settlement/save.jsp");
        assertThat(fields(body)).containsExactly(
                Map.entry("settlementAmount", "1000.00"),
                Map.entry("savedBy", "supervisor"));

        ArgumentCaptor<Settlement> saved = ArgumentCaptor.forClass(Settlement.class);
        verify(settlements).insert(saved.capture());
        assertThat(saved.getValue().settlementId()).isEqualTo(121);
        assertThat(saved.getValue().claimId()).isEqualTo(119);
        assertThat(saved.getValue().calculatedBy()).isEqualTo("supervisor");
        assertThat(saved.getValue().calculatedDate()).isEqualTo("2019-04-01");
    }

    /** SETTLE-R37: calculate persists nothing. */
    @Test
    void settleR37CalculateDoesNotPersist() throws Exception {
        mvc.perform(post("/claims/settlement/calculate.do").param("claimId", "119"));

        verify(settlements, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    /** SETTLE-R15, SETTLE-R18: a failing save answers HTTP 200 with the generic error page. */
    @Test
    void settleR15SaveWithUnknownClaimForwardsToTheErrorPage() throws Exception {
        when(claims.findPolicyId(anyInt())).thenReturn(null);

        MvcResult result = mvc.perform(post("/claims/settlement/save.do").param("claimId", "999999"))
                .andExpect(status().isOk()).andReturn();

        assertThat(view(body(result))).isEqualTo("/WEB-INF/jsp/error.jsp");
    }

    /** SETTLE-R03, SETTLE-R45: detail renders the highest-id settlement of the claim. */
    @Test
    void settleR45DetailRendersTheLatestSettlement() throws Exception {
        when(settlements.findLatestByClaim(119)).thenReturn(new Settlement(
                121, 119, 5000.00, 500.00, 0.00, true, 1000.00, "supervisor", "2019-04-01"));

        String body = body(mvc.perform(post("/claims/settlement/detail.do").param("claimId", "119")).andReturn());

        assertThat(view(body)).isEqualTo("/WEB-INF/jsp/settlement/detail.jsp");
        assertThat(fields(body)).containsEntry("detailSettlementId", "121")
                .containsEntry("detailAmount", "1000.00")
                .containsEntry("detailCapped", "true")
                .containsEntry("detailDate", "2019-04-01");
    }

    /**
     * SETTLE-R46: a claim without settlements reaches the generic error page, because
     * {@code detail.jsp} line 24 reads {@code calculatedBy} off the missing bean.
     */
    @Test
    void settleR46DetailWithoutSettlementForwardsToTheErrorPage() throws Exception {
        String body = body(mvc.perform(post("/claims/settlement/detail.do").param("claimId", "118"))
                .andExpect(status().isOk()).andReturn());

        assertThat(view(body)).isEqualTo("/WEB-INF/jsp/error.jsp");
        assertThat(fields(body)).isEmpty();
    }

    /**
     * SETTLE-R01, SETTLE-R03: the legacy mappings accept GET as well as POST, so the extracted
     * routes answer a GET with the same screen.
     */
    @Test
    void settleR01GetIsAcceptedLikeTheLegacyMapping() throws Exception {
        when(claims.findPolicyId(119)).thenReturn(9001);
        when(policies.findPolicyLimit(9001)).thenReturn(1000.00);

        String body = body(mvc.perform(get("/claims/settlement/calculate.do")
                .param("claimId", "119").param("coveredAmount", "5000")
                .param("deductible", "500").param("depreciation", "0")).andReturn());

        assertThat(view(body)).isEqualTo("/WEB-INF/jsp/settlement/calculate.jsp");
        assertThat(fields(body)).containsEntry("settlementAmount", "1000.00");
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static String view(String body) {
        Matcher matcher = VIEW.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Map<String, String> fields(String body) {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher matcher = FIELD.matcher(body);
        while (matcher.find()) {
            found.put(matcher.group(1), matcher.group(2));
        }
        return found;
    }

    private static java.util.List<String> errors(String body) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        Matcher matcher = ERROR.matcher(body);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
