package com.northstar.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Rule-by-rule cover of the settlement arithmetic of SPEC-SETTLE-001 section 3.3. */
class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    /** SETTLE-R21, SETTLE-R22: depreciation is subtracted first, then the deductible. */
    @Test
    void settleR21R22GrossLossIsCoveredMinusDepreciationThenDeductible() {
        Settlement result = calculator.calculate(5000.00, "500", 1000.00, 100000.00);

        assertThat(result.settlementAmount()).isEqualTo(3500.00);
        assertThat(result.cappedAtLimit()).isFalse();
    }

    /** SETTLE-R23: a deductible larger than the loss floors the settlement at 0.00. */
    @Test
    void settleR23DeductibleAboveLossFloorsAtZero() {
        Settlement result = calculator.calculate(1000.00, "2000", 0.00, 100000.00);

        assertThat(result.settlementAmount()).isEqualTo(0.00);
    }

    /** SETTLE-R30: the deductible is reported in full even when the loss was floored. */
    @Test
    void settleR30DeductibleAppliedReportsTheFullParsedValue() {
        Settlement result = calculator.calculate(1000.00, "2000", 0.00, 100000.00);

        assertThat(result.deductibleApplied()).isEqualTo(2000.00);
    }

    /** SETTLE-R24, SETTLE-R25: the cap applies only above the limit, never at it. */
    @Test
    void settleR24R25CapAppliesOnlyStrictlyAboveThePolicyLimit() {
        Settlement capped = calculator.calculate(20000.00, "100", 0.00, 1000.00);
        Settlement exactlyAtLimit = calculator.calculate(1100.00, "100", 0.00, 1000.00);

        assertThat(capped.cappedAtLimit()).isTrue();
        assertThat(capped.settlementAmount()).isEqualTo(1000.00);
        assertThat(exactlyAtLimit.cappedAtLimit()).isFalse();
        assertThat(exactlyAtLimit.settlementAmount()).isEqualTo(1000.00);
    }

    /** SETTLE-R26: below the limit nothing is capped. */
    @Test
    void settleR26BelowTheLimitTheLossIsPaidInFull() {
        Settlement result = calculator.calculate(5000.00, "", 500.00, 100000.00);

        assertThat(result.cappedAtLimit()).isFalse();
        assertThat(result.settlementAmount()).isEqualTo(4500.00);
    }

    /** SETTLE-R16: an empty deductible is 0.00. */
    @Test
    void settleR16EmptyDeductibleIsZero() {
        assertThat(calculator.calculate(5000.00, "", 500.00, 100000.00).deductibleApplied()).isEqualTo(0.00);
    }

    /** SETTLE-R17: a whitespace-only deductible is trimmed and treated as 0.00. */
    @Test
    void settleR17WhitespaceOnlyDeductibleIsZero() {
        assertThat(calculator.calculate(5000.00, "   ", 500.00, 100000.00).deductibleApplied()).isEqualTo(0.00);
    }

    /** SETTLE-R18: a non-blank, non-numeric deductible throws out of the calculator. */
    @Test
    void settleR18NonNumericDeductibleThrows() {
        assertThatThrownBy(() -> calculator.calculate(1000.00, "abc", 0.00, 100000.00))
                .isInstanceOf(NumberFormatException.class);
    }

    /** SETTLE-R19: negative inputs are calculated as submitted, without any range check. */
    @Test
    void settleR19NegativeInputsAreNotRejected() {
        Settlement result = calculator.calculate(-100.00, "0", 0.00, 100000.00);

        assertThat(result.settlementAmount()).isEqualTo(0.00);
        assertThat(result.coveredAmount()).isEqualTo(-100.00);
    }

    /**
     * SETTLE-R28, SETTLE-R29: rounding is {@code Math.round(amount * 100.0) / 100.0} over binary
     * doubles, so 1.005 settles at 1.00 while the covered amount displays as 1.01
     * (the {@code settlement_half_cent} transcript).
     */
    @Test
    void settleR28R29HalfCentRoundsDownInTheCalculationAndUpOnTheScreen() {
        Settlement result = calculator.calculate(1.005, "0", 0.00, 100000.00);

        assertThat(result.settlementAmount()).isEqualTo(1.00);
        assertThat(String.format(java.util.Locale.US, "%.2f", result.coveredAmount())).isEqualTo("1.01");
    }

    /** SETTLE-R31: the covered amount and depreciation are echoed unrounded. */
    @Test
    void settleR31InputsAreEchoedUnrounded() {
        Settlement result = calculator.calculate(1.005, "0", 0.004, 100000.00);

        assertThat(result.coveredAmount()).isEqualTo(1.005);
        assertThat(result.depreciation()).isEqualTo(0.004);
    }

    /** SETTLE-R32: the capped amount is rounded like any other amount. */
    @Test
    void settleR32CappedAmountIsAlsoRounded() {
        Settlement result = calculator.calculate(20000.00, "0", 0.00, 1000.005);

        assertThat(result.cappedAtLimit()).isTrue();
        assertThat(result.settlementAmount()).isEqualTo(1000.01);
    }
}
