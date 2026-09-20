package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the legacy calculation rules SETTLE-R21..R33 value for value.
 */
class SettlementCalculatorTest {

    @Test
    @DisplayName("SETTLE-R21: gross loss is covered minus depreciation")
    void settleR21GrossLossSubtractsDepreciation() {
        SettlementResult result = SettlementCalculator.calculate(5000, "", 500, 100000);
        assertThat(result.settlementAmount()).isEqualTo(4500.00);
    }

    @Test
    @DisplayName("SETTLE-R22: deductible is subtracted from the gross loss")
    void settleR22DeductibleSubtracted() {
        SettlementResult result = SettlementCalculator.calculate(5000, "500.00", 0, 100000);
        assertThat(result.settlementAmount()).isEqualTo(4500.00);
        assertThat(result.deductibleApplied()).isEqualTo(500.00);
    }

    @Test
    @DisplayName("SETTLE-R23: amount after deductible is floored at zero")
    void settleR23FloorAtZero() {
        SettlementResult result = SettlementCalculator.calculate(1000, "2000.00", 0, 100000);
        assertThat(result.settlementAmount()).isEqualTo(0.00);
        assertThat(result.cappedAtLimit()).isFalse();
    }

    @Test
    @DisplayName("SETTLE-R24: strictly above the limit caps at the limit and flags cappedAtLimit")
    void settleR24CapAtLimit() {
        SettlementResult result = SettlementCalculator.calculate(20000, "100.00", 0, 1000);
        assertThat(result.cappedAtLimit()).isTrue();
        assertThat(result.settlementAmount()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("SETTLE-R25: exactly the limit is not capped")
    void settleR25EqualToLimitNotCapped() {
        SettlementResult result = SettlementCalculator.calculate(1500, "500", 0, 1000);
        assertThat(result.cappedAtLimit()).isFalse();
        assertThat(result.settlementAmount()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("SETTLE-R26: at or below the limit, including floored to zero, is not capped")
    void settleR26BelowLimitNotCapped() {
        assertThat(SettlementCalculator.calculate(5000, "", 500, 100000).cappedAtLimit()).isFalse();
        assertThat(SettlementCalculator.calculate(1000, "2000", 0, 100000).cappedAtLimit()).isFalse();
    }

    @Test
    @DisplayName("SETTLE-R27: a zero policy limit forces any positive amount to zero")
    void settleR27ZeroLimitForcesZero() {
        SettlementResult result = SettlementCalculator.calculate(5000, "", 0, 0);
        assertThat(result.cappedAtLimit()).isTrue();
        assertThat(result.settlementAmount()).isEqualTo(0.00);
    }

    @Test
    @DisplayName("SETTLE-R28: legacy quirk - binary-double Math.round pays 1.00 for a covered amount of 1.005")
    void settleR28HalfCentRoundsDownInBinaryDouble() {
        SettlementResult result = SettlementCalculator.calculate(1.005, "", 0, 100000);
        assertThat(result.settlementAmount()).isEqualTo(1.00);
    }

    @Test
    @DisplayName("SETTLE-R29: legacy quirk - the same 1.005 displays as 1.01 but settles at 1.00")
    void settleR29DisplayAndCalculationRoundDifferently() {
        SettlementResult result = SettlementCalculator.calculate(1.005, "", 0, 100000);
        assertThat(LegacyDisplay.money(result.coveredAmount())).isEqualTo("1.01");
        assertThat(LegacyDisplay.money(result.settlementAmount())).isEqualTo("1.00");
    }

    @Test
    @DisplayName("SETTLE-R30: deductibleApplied reports the full deductible even when floored away")
    void settleR30DeductibleAppliedIsFullDeductible() {
        SettlementResult result = SettlementCalculator.calculate(1000, "2000.00", 0, 100000);
        assertThat(result.deductibleApplied()).isEqualTo(2000.00);
        assertThat(result.settlementAmount()).isEqualTo(0.00);
    }

    @Test
    @DisplayName("SETTLE-R31: coveredAmount and depreciation are echoed unrounded")
    void settleR31InputsEchoedUnrounded() {
        SettlementResult result = SettlementCalculator.calculate(1.005, "", 0.004, 100000);
        assertThat(result.coveredAmount()).isEqualTo(1.005);
        assertThat(result.depreciation()).isEqualTo(0.004);
    }

    @Test
    @DisplayName("SETTLE-R32: rounding happens once, after cap and floor")
    void settleR32RoundsOnceAfterCapAndFloor() {
        // 1000.004 is compared to the limit unrounded, so it is capped; rounding first would not cap.
        SettlementResult result = SettlementCalculator.calculate(1000.004, "", 0, 1000);
        assertThat(result.cappedAtLimit()).isTrue();
        assertThat(result.settlementAmount()).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("SETTLE-R33: only the settlement is compared to the limit, inputs above it are not errors")
    void settleR33InputsAboveLimitAreNotErrors() {
        SettlementResult result = SettlementCalculator.calculate(1000, "2000.00", 0, 1000);
        assertThat(result.deductibleApplied()).isEqualTo(2000.00);
        assertThat(result.settlementAmount()).isEqualTo(0.00);
    }

    @Test
    @DisplayName("SETTLE-R16: a blank deductible string is zero")
    void settleR16BlankDeductibleIsZero() {
        assertThat(SettlementCalculator.calculate(5000, "", 0, 100000).deductibleApplied()).isEqualTo(0.0);
        assertThat(SettlementCalculator.calculate(5000, null, 0, 100000).deductibleApplied()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("SETTLE-R17: a whitespace-only deductible is trimmed and treated as zero")
    void settleR17WhitespaceDeductibleIsZero() {
        assertThat(SettlementCalculator.calculate(5000, "   ", 0, 100000).deductibleApplied()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("SETTLE-R18 (save route; calculate rejects it first under SETTLE-R18 v2): the calculator has no"
            + " fallback for a non-numeric deductible and throws NumberFormatException")
    void settleR18NonNumericDeductibleThrows() {
        assertThatThrownBy(() -> SettlementCalculator.calculate(1000, "abc", 0, 100000))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("SETTLE-R19: negative and very large amounts are calculated as submitted")
    void settleR19NoRangeChecks() {
        SettlementResult negative = SettlementCalculator.calculate(-500, "", 0, 100000);
        assertThat(negative.settlementAmount()).isEqualTo(0.00);
        SettlementResult large = SettlementCalculator.calculate(1e12, "", 0, 1e15);
        assertThat(large.settlementAmount()).isEqualTo(1e12);
    }
}
