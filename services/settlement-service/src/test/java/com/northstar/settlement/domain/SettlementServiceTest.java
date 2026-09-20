package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.settlement.persistence.ClaimRecord;
import com.northstar.settlement.persistence.ClaimRepository;
import com.northstar.settlement.persistence.PolicyRepository;
import com.northstar.settlement.persistence.SettlementRepository;
import com.northstar.settlement.persistence.SettlementRow;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pins the action-level rules SETTLE-R14, R15, R37..R41, R45 with the repositories mocked.
 */
class SettlementServiceTest {

    private ClaimRepository claims;
    private PolicyRepository policies;
    private SettlementRepository settlements;
    private SettlementService service;

    @BeforeEach
    void setUp() {
        claims = mock(ClaimRepository.class);
        policies = mock(PolicyRepository.class);
        settlements = mock(SettlementRepository.class);
        service = new SettlementService(claims, policies, settlements, "supervisor");
        when(claims.findById(119)).thenReturn(Optional.of(new ClaimRecord(119, 9001, "DENIED")));
        when(claims.findById(120)).thenReturn(Optional.of(new ClaimRecord(120, 9002, "CLOSED")));
        when(policies.findLimit(9001)).thenReturn(Optional.of(1000.0));
        when(policies.findLimit(9002)).thenReturn(Optional.of(100000.0));
        when(settlements.nextId()).thenReturn(121);
    }

    @Test
    @DisplayName("SETTLE-R14: calculate takes the limit from the claim's policy")
    void settleR14LimitFromClaimPolicy() {
        CalculatedSettlement calculated = service.calculate(
                new SettlementRequest("119", "5000.00", "500.00", "0.00", null));
        assertThat(calculated.policyLimit()).isEqualTo(1000.0);
        assertThat(calculated.result().cappedAtLimit()).isTrue();
        assertThat(calculated.result().settlementAmount()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("SETTLE-R14: legacy quirk - calculate falls back to a 10000 limit when the claim is missing")
    void settleR14FallbackLimitWhenClaimMissing() {
        when(claims.findById(999)).thenReturn(Optional.empty());
        CalculatedSettlement calculated = service.calculate(
                new SettlementRequest("999", "20000", "", "0", null));
        assertThat(calculated.policyLimit()).isEqualTo(10000.0);
        assertThat(calculated.result().settlementAmount()).isEqualTo(10000.00);
    }

    @Test
    @DisplayName("SETTLE-R14: legacy quirk - calculate falls back to 10000 when the claim's policy is missing")
    void settleR14FallbackLimitWhenPolicyMissing() {
        when(claims.findById(7)).thenReturn(Optional.of(new ClaimRecord(7, 42, "OPEN")));
        when(policies.findLimit(42)).thenReturn(Optional.empty());
        assertThat(service.calculate(new SettlementRequest("7", null, null, null, null)).policyLimit())
                .isEqualTo(10000.0);
    }

    @Test
    @DisplayName("SETTLE-R11 / SETTLE-R12 / SETTLE-R13: a bare calculate request uses claim 119, 5000 and 0")
    void settleR11R12R13BareRequestUsesFallbacks() {
        CalculatedSettlement calculated = service.calculate(new SettlementRequest(null, null, null, null, null));
        assertThat(calculated.claimId()).isEqualTo(119);
        assertThat(calculated.result().coveredAmount()).isEqualTo(5000.0);
        assertThat(calculated.result().depreciation()).isEqualTo(0.0);
        assertThat(calculated.result().deductibleApplied()).isEqualTo(0.0);
        assertThat(calculated.result().settlementAmount()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("SETTLE-R15: legacy quirk - save has no fallback limit and fails for a missing claim")
    void settleR15SaveFailsForMissingClaim() {
        when(claims.findById(999)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.save(new SettlementRequest("999", "5000", "", "0", null)))
                .isInstanceOf(ClaimNotFoundException.class);
        verify(settlements, never()).insert(any());
    }

    @Test
    @DisplayName("SETTLE-R37 / SETTLE-R42: save recomputes from the request and stores the calculator output")
    void settleR37R42SaveRecomputesAndStoresCalculatorOutput() {
        SettlementRow saved = service.save(new SettlementRequest("119", "5000.00", "500.00", "0.00", null));
        ArgumentCaptor<SettlementRow> inserted = ArgumentCaptor.forClass(SettlementRow.class);
        verify(settlements).insert(inserted.capture());
        assertThat(inserted.getValue()).isEqualTo(saved);
        assertThat(saved.coveredAmount()).isEqualTo(5000.0);
        assertThat(saved.deductibleApplied()).isEqualTo(500.0);
        assertThat(saved.depreciation()).isEqualTo(0.0);
        assertThat(saved.cappedAtLimit()).isTrue();
        assertThat(saved.settlementAmount()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("SETTLE-R38 / SETTLE-R39: save inserts a new row with max(settlement_id)+1")
    void settleR38R39SaveAllocatesNextIdAndInserts() {
        SettlementRow saved = service.save(new SettlementRequest("119", "5000.00", "500.00", "0.00", null));
        assertThat(saved.settlementId()).isEqualTo(121);
        assertThat(saved.claimId()).isEqualTo(119);
    }

    @Test
    @DisplayName("SETTLE-R40 / SETTLE-R48: calculated_by is the configured operator via String.valueOf")
    void settleR40R48CalculatedByIsOperator() {
        SettlementRow saved = service.save(new SettlementRequest("119", null, null, null, null));
        assertThat(saved.calculatedBy()).isEqualTo("supervisor");
        SettlementService anonymous = new SettlementService(claims, policies, settlements, null);
        assertThat(anonymous.save(new SettlementRequest("119", null, null, null, null)).calculatedBy())
                .isEqualTo("null");
    }

    @Test
    @DisplayName("SETTLE-R41: legacy quirk - calculated_date is always 2019-04-01")
    void settleR41CalculatedDateIsFixed() {
        SettlementRow saved = service.save(new SettlementRequest("119", null, null, null, null));
        assertThat(saved.calculatedDate()).isEqualTo(LocalDate.of(2019, 4, 1));
    }

    @Test
    @DisplayName("SETTLE-R03 / SETTLE-R45 / SETTLE-R46: detail returns the highest-id row or nothing")
    void settleR03R45R46DetailReturnsLatestOrEmpty() {
        SettlementRow row = new SettlementRow(121, 119, 5000, 500, 0, true, 1000, "supervisor",
                LocalDate.of(2019, 4, 1));
        when(settlements.findLatestByClaim(119)).thenReturn(Optional.of(row));
        when(settlements.findLatestByClaim(5)).thenReturn(Optional.empty());
        assertThat(service.detail(119)).contains(row);
        assertThat(service.detail(5)).isEmpty();
    }
}
