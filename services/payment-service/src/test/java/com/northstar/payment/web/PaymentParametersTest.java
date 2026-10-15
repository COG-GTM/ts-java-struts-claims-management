package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the ClaimsActionSupport coercions the payment actions inherit. */
class PaymentParametersTest {

    /**
     * Rules: PAY-R02.
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
     * Rules: PAY-R20.
     * Missing or unparseable paymentId falls back to 61
     * (src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17).
     */
    @Test
    void paymentIdFallsBackTo61() {
        assertThat(LegacyParameters.integer(null, LegacyParameters.DEFAULT_PAYMENT_ID)).isEqualTo(61);
        assertThat(LegacyParameters.integer("x", LegacyParameters.DEFAULT_PAYMENT_ID)).isEqualTo(61);
    }

    /**
     * Rules: PAY-R05.
     * Missing or unparseable amount falls back to the settlement amount; a
     * parseable one is taken as-is with no rounding
     * (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45).
     */
    @Test
    void amountFallsBackToTheSettlementAmount() {
        assertThat(LegacyParameters.decimal(null, 1000.0)).isEqualTo(1000.0);
        assertThat(LegacyParameters.decimal("", 1000.0)).isEqualTo(1000.0);
        assertThat(LegacyParameters.decimal("ten", 1000.0)).isEqualTo(1000.0);
        assertThat(LegacyParameters.decimal("$10", 1000.0)).isEqualTo(1000.0);
        assertThat(LegacyParameters.decimal("12.345", 1000.0)).isEqualTo(12.345);
    }

    /**
     * Rules: PAY-R10.
     * The check number is CHK- plus the unpadded id
     * (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33).
     */
    @Test
    void checkNumberIsNotZeroPadded() {
        assertThat(LegacyParameters.checkNumber(61)).isEqualTo("CHK-61");
        assertThat(LegacyParameters.checkNumber(1)).isEqualTo("CHK-1");
    }

    /**
     * Rules: PAY-R27, PAY-R28.
     * Money prints with two decimals, dates as yyyy-MM-dd, null as empty
     * (src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-85).
     */
    @Test
    void fieldsFormatAsTheTagWould() {
        assertThat(FieldFormatter.money(1000.0)).isEqualTo("1000.00");
        assertThat(FieldFormatter.money(12.345)).isEqualTo("12.35");
        assertThat(FieldFormatter.integer(61)).isEqualTo("61");
        assertThat(FieldFormatter.date("2019-04-03")).isEqualTo("2019-04-03");
        assertThat(FieldFormatter.date(null)).isEqualTo("");
        assertThat(FieldFormatter.text(null)).isEqualTo("");
    }
}
