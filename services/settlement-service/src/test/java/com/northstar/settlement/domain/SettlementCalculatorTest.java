package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** SETTLE-R07, SETTLE-R08: transcripts/settlement_calculate.json. */
    @Test
    void capsAtPolicyLimit() {
        SettlementResult result = calculator.calculate(d("5000.00"),
                d("500.00"), d("0.00"), d("1000"));
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("1000.00");
        assertThat(result.cappedAtLimit()).isTrue();
    }

    /** SETTLE-R07: transcripts/settlement_blank_deductible.json. */
    @Test
    void subtractsDepreciationAndDeductible() {
        SettlementResult result = calculator.calculate(d("5000.00"),
                BigDecimal.ZERO, d("500.00"), d("100000"));
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("4500.00");
        assertThat(result.cappedAtLimit()).isFalse();
    }

    /** SETTLE-R07: transcripts/settlement_deductible_floor.json. */
    @Test
    void floorsAtZero() {
        SettlementResult result = calculator.calculate(d("1000.00"),
                d("2000.00"), d("0.00"), d("100000"));
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("0.00");
    }

    /**
     * SETTLE-R09, QUIRK-01: transcripts/settlement_half_cent.json. Legacy
     * double arithmetic settles 1.005 at 1.00; BigDecimal HALF_UP would say
     * 1.01 and the parity run caught exactly that.
     */
    @Test
    void halfCentUsesLegacyDoubleMath() {
        SettlementResult result = calculator.calculate(d("1.005"),
                BigDecimal.ZERO, d("0.00"), d("100000"));
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("1.00");
        assertThat(LegacyDisplay.money(result.coveredAmount()))
                .isEqualTo("1.01");
    }

    /** SETTLE-R08: transcripts/settlement_policy_cap.json. */
    @Test
    void capReportsCapped() {
        SettlementResult result = calculator.calculate(d("20000.00"),
                d("100.00"), d("0.00"), d("1000"));
        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("1000.00");
        assertThat(result.cappedAtLimit()).isTrue();
    }
}
