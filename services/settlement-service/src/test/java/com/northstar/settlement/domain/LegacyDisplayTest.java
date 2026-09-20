package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the ns:field presentation rules SETTLE-R34 and SETTLE-R35.
 */
class LegacyDisplayTest {

    @Test
    @DisplayName("SETTLE-R34: money renders with exactly two decimals")
    void settleR34MoneyTwoDecimals() {
        assertThat(LegacyDisplay.money(5000)).isEqualTo("5000.00");
        assertThat(LegacyDisplay.money(0)).isEqualTo("0.00");
        assertThat(LegacyDisplay.money(1.0)).isEqualTo("1.00");
        assertThat(LegacyDisplay.money(4500)).isEqualTo("4500.00");
    }

    @Test
    @DisplayName("SETTLE-R29: legacy quirk - the money formatter rounds 1.005 up to 1.01")
    void settleR29MoneyFormatterRoundsHalfCentUp() {
        assertThat(LegacyDisplay.money(1.005)).isEqualTo("1.01");
    }

    @Test
    @DisplayName("SETTLE-R35: legacy quirk - cappedAtLimit is emitted as literal true/false despite type=money")
    void settleR35FlagRendersLiteralBoolean() {
        assertThat(LegacyDisplay.flag(true)).isEqualTo("true");
        assertThat(LegacyDisplay.flag(false)).isEqualTo("false");
    }

    @Test
    @DisplayName("Integer fields render as whole numbers")
    void integerRendersWholeNumber() {
        assertThat(LegacyDisplay.integer(121)).isEqualTo("121");
    }
}
