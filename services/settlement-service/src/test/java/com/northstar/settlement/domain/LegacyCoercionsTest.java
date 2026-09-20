package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class LegacyCoercionsTest {

    /** SETTLE-R02. */
    @Test
    void unparsableClaimIdFallsBack() {
        assertThat(LegacyCoercions.integer(null, 119)).isEqualTo(119);
        assertThat(LegacyCoercions.integer("x", 119)).isEqualTo(119);
        assertThat(LegacyCoercions.integer("120", 119)).isEqualTo(120);
    }

    /** SETTLE-R03. */
    @Test
    void unparsableAmountFallsBack() {
        assertThat(LegacyCoercions.decimal(null, 5000)).isEqualTo(5000);
        assertThat(LegacyCoercions.decimal("abc", 0)).isEqualTo(0);
    }

    /**
     * SETTLE-R03, SETTLE-R06, QUIRK-08: the Struts screens parse with
     * Double.parseDouble, so the forms it accepts stay accepted, and a
     * deductible in one of those forms is not a validation error (CHG-001
     * covers only what the legacy parser also rejects).
     */
    @Test
    void acceptsEveryLegacyDoubleForm() {
        assertThat(LegacyCoercions.decimal("0x1.0p0", 5000)).isEqualTo(1.0);
        assertThat(LegacyCoercions.decimal("1d", 5000)).isEqualTo(1.0);
        assertThat(LegacyCoercions.decimal(" 7 ", 5000)).isEqualTo(7.0);
        assertThat(LegacyCoercions.decimal("Infinity", 5000))
                .isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(LegacyCoercions.decimal("NaN", 5000)).isNaN();
        assertThat(LegacyCoercions.deductible("1d")).isEqualTo(1.0);
        assertThat(LegacyCoercions.deductible("0x1p1")).isEqualTo(2.0);
    }

    /** SETTLE-R04. */
    @Test
    void blankDeductibleIsZero() {
        assertThat(LegacyCoercions.deductible("")).isEqualTo(0);
        assertThat(LegacyCoercions.deductible(null)).isEqualTo(0);
    }

    /** SETTLE-R06. */
    @Test
    void nonNumericDeductibleFails() {
        assertThatThrownBy(() -> LegacyCoercions.deductible("abc"))
                .isInstanceOf(NumberFormatException.class);
    }
}
