package com.northstar.settlement.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the lenient request-parameter coercion of the Struts actions. */
class LegacyParametersTest {

    /**
     * Rules: SETTLE-R02.
     * Transcript: settlement_calculate (claimId "119" is parsed, not defaulted);
     * the fallback itself is read from
     * src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37.
     */
    @Test
    void claimIdFallsBackTo119() {
        assertThat(LegacyParameters.integer("119", 119)).isEqualTo(119);
        assertThat(LegacyParameters.integer("120", 119)).isEqualTo(120);
        assertThat(LegacyParameters.integer(null, 119)).isEqualTo(119);
        assertThat(LegacyParameters.integer("", 119)).isEqualTo(119);
        assertThat(LegacyParameters.integer("abc", 119)).isEqualTo(119);
        assertThat(LegacyParameters.integer("119.0", 119)).isEqualTo(119);
    }

    /**
     * Rules: SETTLE-R03, SETTLE-R04.
     * Transcript: none — read from
     * src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45 and
     * src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:32-33.
     */
    @Test
    void amountsFallBackTo5000And0() {
        assertThat(LegacyParameters.decimal("5000.00", 5000)).isEqualTo(5000.0);
        assertThat(LegacyParameters.decimal("1.005", 5000)).isEqualTo(1.005);
        assertThat(LegacyParameters.decimal(null, 5000)).isEqualTo(5000.0);
        assertThat(LegacyParameters.decimal("abc", 5000)).isEqualTo(5000.0);
        assertThat(LegacyParameters.decimal(null, 0)).isEqualTo(0.0);
        assertThat(LegacyParameters.decimal("", 0)).isEqualTo(0.0);
    }

    /**
     * Rules: SETTLE-R05.
     * Transcript: settlement_blank_deductible (deductible "" settles with
     * deductibleApplied 0.00);
     * Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:34-37.
     */
    @Test
    void nullOrEmptyDeductibleBecomesTheStringZero() {
        assertThat(LegacyParameters.deductibleOrZero(null)).isEqualTo("0");
        assertThat(LegacyParameters.deductibleOrZero("")).isEqualTo("0");
        assertThat(LegacyParameters.deductibleOrZero("500.00")).isEqualTo("500.00");
        assertThat(LegacyParameters.deductibleOrZero("abc")).isEqualTo("abc");
        assertThat(LegacyParameters.deductibleOrZero("  ")).isEqualTo("  ");
    }

    /**
     * Rules: SETTLE-R05, SETTLE-R06 (v2).
     * Change: docs/changes/CHG-001-invalid-deductible.md — only a non-blank
     * value that Double.parseDouble rejects is invalid.
     */
    @Test
    void onlyANonBlankUnparseableDeductibleIsInvalid() {
        assertThat(LegacyParameters.isDeductibleInvalid(null)).isFalse();
        assertThat(LegacyParameters.isDeductibleInvalid("")).isFalse();
        assertThat(LegacyParameters.isDeductibleInvalid("   ")).isFalse();
        assertThat(LegacyParameters.isDeductibleInvalid("500.00")).isFalse();
        assertThat(LegacyParameters.isDeductibleInvalid(" 500.00 ")).isFalse();
        assertThat(LegacyParameters.isDeductibleInvalid("-1")).isFalse();
        assertThat(LegacyParameters.isDeductibleInvalid("abc")).isTrue();
        assertThat(LegacyParameters.isDeductibleInvalid("1,000")).isTrue();
        assertThat(LegacyParameters.isDeductibleInvalid("$50")).isTrue();
    }

    /**
     * Rules: SETTLE-R19, SETTLE-R20, SETTLE-R22, SETTLE-R31.
     * Transcript: settlement_calculate (money values carry two decimals,
     * cappedAtLimit renders as "true");
     * Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-76.
     */
    @Test
    void fieldFormattingMatchesTheFieldTag() {
        assertThat(FieldFormatter.money(1000.0)).isEqualTo("1000.00");
        assertThat(FieldFormatter.money(1.005)).isEqualTo("1.01");
        assertThat(FieldFormatter.money(true)).isEqualTo("true");
        assertThat(FieldFormatter.integer(119)).isEqualTo("119");
        assertThat(FieldFormatter.text(null)).isEmpty();
        assertThat(FieldFormatter.date("2019-03-01")).isEqualTo("2019-03-01");
        assertThat(FieldFormatter.date("not a date")).isEqualTo("not a date");
    }
}
