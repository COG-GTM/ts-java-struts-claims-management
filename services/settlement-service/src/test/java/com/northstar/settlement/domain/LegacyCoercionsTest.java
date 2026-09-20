package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
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
        assertThat(LegacyCoercions.decimal(null, "5000"))
                .isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(LegacyCoercions.decimal("abc", "0"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** SETTLE-R04. */
    @Test
    void blankDeductibleIsZero() {
        assertThat(LegacyCoercions.deductible("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(LegacyCoercions.deductible(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** SETTLE-R06. */
    @Test
    void nonNumericDeductibleFails() {
        assertThatThrownBy(() -> LegacyCoercions.deductible("abc"))
                .isInstanceOf(NumberFormatException.class);
    }
}
