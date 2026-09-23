package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for the legacy settlement arithmetic. */
class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    /**
     * Rules: SETTLE-R11, SETTLE-R13.
     * Transcript: settlement_calculate (claim 119, policy 9001 limit 1000).
     */
    @Test
    void netAmountIsCappedAtThePolicyLimit() {
        CalculatedSettlement result = calculator.calculate(5000.00, "500.00", 0.00, 1000);

        assertThat(result.settlementAmount()).isEqualTo(1000.00);
        assertThat(result.cappedAtLimit()).isTrue();
        assertThat(result.deductibleApplied()).isEqualTo(500.00);
    }

    /**
     * Rules: SETTLE-R13.
     * Transcript: settlement_policy_cap.
     */
    @Test
    void largeCoveredAmountStillSettlesAtTheLimit() {
        CalculatedSettlement result = calculator.calculate(20000.00, "100.00", 0.00, 1000);

        assertThat(result.settlementAmount()).isEqualTo(1000.00);
        assertThat(result.cappedAtLimit()).isTrue();
    }

    /**
     * Rules: SETTLE-R05, SETTLE-R10.
     * Transcript: settlement_blank_deductible (claim 120, policy 9002 limit 100000).
     */
    @Test
    void blankDeductibleCountsAsZeroAndDepreciationReducesTheGross() {
        CalculatedSettlement result = calculator.calculate(5000.00, "", 500.00, 100000);

        assertThat(result.deductibleApplied()).isEqualTo(0.00);
        assertThat(result.settlementAmount()).isEqualTo(4500.00);
        assertThat(result.cappedAtLimit()).isFalse();
    }

    /**
     * Rules: SETTLE-R12.
     * Transcript: settlement_deductible_floor.
     */
    @Test
    void deductibleAboveTheGrossFloorsTheSettlementAtZero() {
        CalculatedSettlement result = calculator.calculate(1000.00, "2000.00", 0.00, 100000);

        assertThat(result.settlementAmount()).isEqualTo(0.00);
        assertThat(result.cappedAtLimit()).isFalse();
    }

    /**
     * Rules: SETTLE-R15, SETTLE-R16, SETTLE-R17.
     * Transcript: settlement_half_cent.
     */
    @Test
    void halfCentRoundsDownAndOnlyTheSettlementAmountIsRounded() {
        CalculatedSettlement result = calculator.calculate(1.005, "0", 0.00, 100000);

        assertThat(result.settlementAmount()).isEqualTo(1.00);
        assertThat(result.coveredAmount()).isEqualTo(1.005);
    }

    /**
     * Rules: SETTLE-R06.
     * Transcript: settlement_bad_deductible.
     */
    @Test
    void nonNumericDeductibleThrows() {
        assertThatThrownBy(() -> calculator.calculate(5000.00, "abc", 0.00, 1000))
                .isInstanceOf(NumberFormatException.class);
    }

    /**
     * Rules: SETTLE-R12, SETTLE-R13, SETTLE-R14.
     * Transcript: none — the order of operations is read from
     * src/main/java/com/northstar/claims/service/SettlementCalculator.java:30-37,
     * where the zero floor runs before the cap, so a zero limit cannot produce
     * a negative settlement.
     */
    @Test
    void zeroFloorRunsBeforeTheCap() {
        CalculatedSettlement result = calculator.calculate(100, "0", 0, 0);

        assertThat(result.settlementAmount()).isEqualTo(0.00);
        assertThat(result.cappedAtLimit()).isTrue();
    }

    /**
     * Rules: SETTLE-R13.
     * Transcript: none — read from
     * src/main/java/com/northstar/claims/service/SettlementCalculator.java:35,
     * where the comparison is strictly greater than, so a net exactly equal to
     * the limit is not flagged as capped.
     */
    @Test
    void netEqualToTheLimitIsNotCapped() {
        CalculatedSettlement result = calculator.calculate(1000, "0", 0, 1000);

        assertThat(result.cappedAtLimit()).isFalse();
        assertThat(result.settlementAmount()).isEqualTo(1000.00);
    }
}
