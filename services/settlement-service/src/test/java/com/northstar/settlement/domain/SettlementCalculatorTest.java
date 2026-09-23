package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    /** SETTLE-R07, SETTLE-R08: transcripts/settlement_calculate.json. */
    @Test
    void capsAtPolicyLimit() {
        SettlementResult result = calculator.calculate(5000.00, 500.00, 0.00,
                1000);
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("1000.00");
        assertThat(result.cappedAtLimit()).isTrue();
    }

    /** SETTLE-R07: transcripts/settlement_blank_deductible.json. */
    @Test
    void subtractsDepreciationAndDeductible() {
        SettlementResult result = calculator.calculate(5000.00, 0, 500.00,
                100000);
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("4500.00");
        assertThat(result.cappedAtLimit()).isFalse();
    }

    /** SETTLE-R07: transcripts/settlement_deductible_floor.json. */
    @Test
    void floorsAtZero() {
        SettlementResult result = calculator.calculate(1000.00, 2000.00, 0.00,
                100000);
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("0.00");
    }

    /** SETTLE-R08: transcripts/settlement_policy_cap.json. */
    @Test
    void capReportsCapped() {
        SettlementResult result = calculator.calculate(20000.00, 100.00, 0.00,
                1000);
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("1000.00");
        assertThat(result.cappedAtLimit()).isTrue();
    }

    /**
     * SETTLE-R09 v2, SETTLE-R10, QUIRK-08: transcripts/settlement_half_cent.json.
     * Math.round(1.005 * 100.0) is 100, so the amount is 1.00; the covered
     * amount is displayed by String.format("%.2f") and shows 1.01.
     */
    @Test
    void halfCentRoundsDownButDisplaysCoveredUp() {
        SettlementResult result = calculator.calculate(1.005, 0, 0.00, 100000);
        assertThat(result.settlementAmount()).isEqualTo(1.00);
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("1.00");
        assertThat(LegacyDisplay.money(result.coveredAmount()))
                .isEqualTo("1.01");
    }

    /** SETTLE-R09 v2, QUIRK-08: double arithmetic end to end, no exact-decimal shortcut. */
    @Test
    void arithmeticIsDoubleArithmetic() {
        SettlementResult result = calculator.calculate(0.3, 0.1, 0, 100000);
        assertThat(result.settlementAmount()).isEqualTo(0.2);
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("0.20");
        SettlementResult infinite = calculator.calculate(
                Double.POSITIVE_INFINITY, 0, 0, 1000);
        assertThat(infinite.cappedAtLimit()).isTrue();
        assertThat(LegacyDisplay.money(infinite.settlementAmount()))
                .isEqualTo("1000.00");
        SettlementResult notANumber = calculator.calculate(Double.NaN, 0, 0,
                1000);
        assertThat(notANumber.cappedAtLimit()).isFalse();
        assertThat(LegacyDisplay.money(notANumber.settlementAmount()))
                .isEqualTo("0.00");
        assertThat(LegacyDisplay.money(notANumber.coveredAmount()))
                .isEqualTo("NaN");
    }
}
