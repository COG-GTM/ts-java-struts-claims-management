package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the request-parameter coercions of SETTLE-R11..R13 and SETTLE-R16..R17.
 */
class LegacyCoercionsTest {

    @Test
    @DisplayName("SETTLE-R11: legacy quirk - missing or unparseable claimId falls back to 119")
    void settleR11ClaimIdFallsBackTo119() {
        assertThat(new SettlementRequest(null, null, null, null, null).resolvedClaimId()).isEqualTo(119);
        assertThat(new SettlementRequest("", null, null, null, null).resolvedClaimId()).isEqualTo(119);
        assertThat(new SettlementRequest("abc", null, null, null, null).resolvedClaimId()).isEqualTo(119);
        assertThat(new SettlementRequest("120", null, null, null, null).resolvedClaimId()).isEqualTo(120);
    }

    @Test
    @DisplayName("SETTLE-R12: legacy quirk - missing or unparseable coveredAmount falls back to 5000")
    void settleR12CoveredAmountFallsBackTo5000() {
        assertThat(new SettlementRequest(null, null, null, null, null).resolvedCoveredAmount()).isEqualTo(5000.0);
        assertThat(new SettlementRequest(null, "", null, null, null).resolvedCoveredAmount()).isEqualTo(5000.0);
        assertThat(new SettlementRequest(null, "x", null, null, null).resolvedCoveredAmount()).isEqualTo(5000.0);
        assertThat(new SettlementRequest(null, "1.005", null, null, null).resolvedCoveredAmount()).isEqualTo(1.005);
    }

    @Test
    @DisplayName("SETTLE-R13: legacy quirk - missing or unparseable depreciation falls back to 0")
    void settleR13DepreciationFallsBackToZero() {
        assertThat(new SettlementRequest(null, null, null, null, null).resolvedDepreciation()).isEqualTo(0.0);
        assertThat(new SettlementRequest(null, null, null, "", null).resolvedDepreciation()).isEqualTo(0.0);
        assertThat(new SettlementRequest(null, null, null, "500.00", null).resolvedDepreciation()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("SETTLE-R16: absent or empty deductible becomes \"0\" before the calculator")
    void settleR16BlankDeductibleBecomesZeroString() {
        assertThat(LegacyCoercions.deductible(null)).isEqualTo("0");
        assertThat(LegacyCoercions.deductible("")).isEqualTo("0");
        assertThat(LegacyCoercions.deductible("500.00")).isEqualTo("500.00");
    }

    @Test
    @DisplayName("SETTLE-R17 / SETTLE-R18: whitespace and garbage deductibles pass through untouched")
    void settleR17R18NonEmptyDeductiblePassesThrough() {
        assertThat(LegacyCoercions.deductible("  ")).isEqualTo("  ");
        assertThat(LegacyCoercions.deductible("abc")).isEqualTo("abc");
    }

    @Test
    @DisplayName("SETTLE-R06: the submitted policyLimit is carried but never used to resolve anything")
    void settleR06PolicyLimitIgnored() {
        SettlementRequest request = new SettlementRequest("119", "5000", "500", "0", "1");
        assertThat(request.policyLimit()).isEqualTo("1");
        assertThat(request.resolvedClaimId()).isEqualTo(119);
    }
}
