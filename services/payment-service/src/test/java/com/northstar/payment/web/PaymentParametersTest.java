package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the ClaimsActionSupport coercions the payment actions inherit. */
class PaymentParametersTest {

    /**
     * Rules: PAY-R04, PAY-R18, PAY-R25 (v1 of each).
     * Missing or unparseable claimId falls back to 119
     * (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37).
     */
    @Test
    void claimIdFallsBackTo119() {
        assertThat(LegacyParameters.integer(null, LegacyParameters.DEFAULT_CLAIM_ID)).isEqualTo(119);
        assertThat(LegacyParameters.integer("", LegacyParameters.DEFAULT_CLAIM_ID)).isEqualTo(119);
        assertThat(LegacyParameters.integer("abc", LegacyParameters.DEFAULT_CLAIM_ID)).isEqualTo(119);
        assertThat(LegacyParameters.integer("12.5", LegacyParameters.DEFAULT_CLAIM_ID)).isEqualTo(119);
        assertThat(LegacyParameters.integer("118", LegacyParameters.DEFAULT_CLAIM_ID)).isEqualTo(118);
    }

    /**
     * Rules: PAY-R22 (v1).
     * Missing or unparseable paymentId falls back to 61
     * (src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17).
     */
    @Test
    void paymentIdFallsBackTo61() {
        assertThat(LegacyParameters.integer(null, LegacyParameters.DEFAULT_PAYMENT_ID)).isEqualTo(61);
        assertThat(LegacyParameters.integer("x", LegacyParameters.DEFAULT_PAYMENT_ID)).isEqualTo(61);
    }

    /**
     * Rules: PAY-R06, PAY-R07, PAY-R08 (v1 of each).
     * Missing or unparseable amount (abc, 1,000) falls back to the settlement
     * amount; anything Double.parseDouble accepts is taken as is: -5, 1e3,
     * " 5 ", NaN, 12.345 unrounded
     * (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45;
     * SPEC-PAY-001 live steps L15, L25, L27, L28, L29, L32).
     */
    @Test
    void amountFallsBackToTheSettlementAmountAndAcceptsAnyDouble() {
        assertThat(LegacyParameters.decimal(null, 1118.0)).isEqualTo(1118.0);
        assertThat(LegacyParameters.decimal("", 1118.0)).isEqualTo(1118.0);
        assertThat(LegacyParameters.decimal("abc", 1118.0)).isEqualTo(1118.0);
        assertThat(LegacyParameters.decimal("1,000", 1118.0)).isEqualTo(1118.0);
        assertThat(LegacyParameters.decimal("-5", 1118.0)).isEqualTo(-5.0);
        assertThat(LegacyParameters.decimal("1e3", 1118.0)).isEqualTo(1000.0);
        assertThat(LegacyParameters.decimal(" 5 ", 1118.0)).isEqualTo(5.0);
        assertThat(LegacyParameters.decimal("NaN", 1118.0)).isNaN();
        assertThat(LegacyParameters.decimal("12.345", 1118.0)).isEqualTo(12.345);
    }

    /**
     * Rules: PAY-R14 (v1).
     * The check number is CHK- plus the unpadded id
     * (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33).
     */
    @Test
    void checkNumberIsNotZeroPadded() {
        assertThat(LegacyParameters.checkNumber(61)).isEqualTo("CHK-61");
        assertThat(LegacyParameters.checkNumber(1)).isEqualTo("CHK-1");
    }

    /**
     * Rules: PAY-R28 (v1).
     * Money prints with two decimals (12.345 -> 12.35, -5 -> -5.00, 0 -> 0.00,
     * NaN -> NaN), integers through a long, dates as yyyy-MM-dd, null as empty
     * (src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-77).
     */
    @Test
    void fieldsFormatAsTheTagWould() {
        assertThat(FieldFormatter.money(1000.0)).isEqualTo("1000.00");
        assertThat(FieldFormatter.money(12.345)).isEqualTo("12.35");
        assertThat(FieldFormatter.money(-5.0)).isEqualTo("-5.00");
        assertThat(FieldFormatter.money(0.0)).isEqualTo("0.00");
        assertThat(FieldFormatter.money(Double.NaN)).isEqualTo("NaN");
        assertThat(FieldFormatter.integer(61)).isEqualTo("61");
        assertThat(FieldFormatter.date("2019-04-03")).isEqualTo("2019-04-03");
        assertThat(FieldFormatter.date(null)).isEqualTo("");
        assertThat(FieldFormatter.text(null)).isEqualTo("");
    }
}
