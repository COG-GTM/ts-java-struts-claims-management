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

    /** SETTLE-R03 v2. */
    @Test
    void unparsableAmountFallsBack() {
        assertThat(LegacyCoercions.decimal(null, 5000)).isEqualTo(5000);
        assertThat(LegacyCoercions.decimal("abc", 0)).isEqualTo(0);
    }

    /** SETTLE-R03 v2, QUIRK-08: every spelling Double.parseDouble accepts is a valid amount. */
    @Test
    void acceptsEverySpellingDoubleParseDoubleAccepts() {
        assertThat(LegacyCoercions.decimal("1d", 0)).isEqualTo(1.0);
        assertThat(LegacyCoercions.decimal("0x1.0p0", 0)).isEqualTo(1.0);
        assertThat(LegacyCoercions.decimal("Infinity", 0))
                .isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(LegacyCoercions.decimal("NaN", 0)).isNaN();
        assertThat(LegacyCoercions.decimal("  5000.00 \t", 0)).isEqualTo(5000.0);

        assertThat(LegacyCoercions.deductible("1d")).isEqualTo(1.0);
        assertThat(LegacyCoercions.deductible("0x1.0p0")).isEqualTo(1.0);
        assertThat(LegacyCoercions.deductible("Infinity"))
                .isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(LegacyCoercions.deductible("NaN")).isNaN();
        assertThat(LegacyCoercions.deductible(" 500.00 ")).isEqualTo(500.0);
    }

    /** QUIRK-08: what Double.parseDouble rejects, the legacy code rejects too. */
    @Test
    void rejectsWhatDoubleParseDoubleRejects() {
        assertThat(LegacyCoercions.decimal("1,000", 0)).isEqualTo(0);
        assertThat(LegacyCoercions.decimal("", 0)).isEqualTo(0);
        assertThatThrownBy(() -> LegacyCoercions.deductible("1,000"))
                .isInstanceOf(NumberFormatException.class);
    }

    /** SETTLE-R04. */
    @Test
    void blankDeductibleIsZero() {
        assertThat(LegacyCoercions.deductible("")).isEqualTo(0);
        assertThat(LegacyCoercions.deductible("   ")).isEqualTo(0);
        assertThat(LegacyCoercions.deductible(null)).isEqualTo(0);
    }

    /** SETTLE-R06. */
    @Test
    void nonNumericDeductibleFails() {
        assertThatThrownBy(() -> LegacyCoercions.deductible("abc"))
                .isInstanceOf(NumberFormatException.class);
    }
}
