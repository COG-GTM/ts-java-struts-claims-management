package com.northstar.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the ClaimsActionSupport coercions the payment actions use. */
class LegacyParametersTest {

    /**
     * Rules: PAY-R02, PAY-R16. integer() falls back on null and on text
     * (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37);
     * live L19, L20 (claimId), live L28, L41 (paymentId).
     */
    @Test
    void integerFallsBackOnMissingAndUnparseable() {
        assertThat(LegacyParameters.integer(null, 119)).isEqualTo(119);
        assertThat(LegacyParameters.integer("abc", 61)).isEqualTo(61);
        assertThat(LegacyParameters.integer("120", 119)).isEqualTo(120);
    }

    /**
     * Rules: PAY-R04, PAY-R05. decimal() falls back on null and on "abc" and
     * otherwise returns Double.parseDouble unchanged: -250, 1.005, NaN, 1e3
     * (ClaimsActionSupport.java:39-45); live L06, L07, L08, L09, L11, L12.
     */
    @Test
    void decimalIsParseDoubleWithFallback() {
        assertThat(LegacyParameters.decimal(null, 1000.0)).isEqualTo(1000.0);
        assertThat(LegacyParameters.decimal("abc", 1000.0)).isEqualTo(1000.0);
        assertThat(LegacyParameters.decimal("-250.00", 0)).isEqualTo(-250.0);
        assertThat(LegacyParameters.decimal("1.005", 0)).isEqualTo(1.005);
        assertThat(LegacyParameters.decimal("NaN", 0)).isNaN();
        assertThat(LegacyParameters.decimal("1e3", 0)).isEqualTo(1000.0);
    }

    /**
     * Rules: PAY-R05, PAY-R20. type="money" is String.format("%.2f")
     * (src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-58): 1.005
     * prints 1.01 (live L09), NaN prints NaN (live L11, L32).
     */
    @Test
    void moneyFormatsWithTwoDecimals() {
        assertThat(FieldFormatter.money(1.005)).isEqualTo("1.01");
        assertThat(FieldFormatter.money(-250)).isEqualTo("-250.00");
        assertThat(FieldFormatter.money(Double.NaN)).isEqualTo("NaN");
    }

    /**
     * Rule: PAY-R18. bean:write escapes markup in the payee and keeps the
     * method's case (src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14,18);
     * live L30 payee_markup "&lt;b&gt;A&amp;B&lt;/b&gt;", live L31 "check".
     */
    @Test
    void escapeMatchesBeanWrite() {
        assertThat(FieldFormatter.escape("<b>A&B</b>")).isEqualTo("&lt;b&gt;A&amp;B&lt;/b&gt;");
        assertThat(FieldFormatter.escape("check")).isEqualTo("check");
    }
}
