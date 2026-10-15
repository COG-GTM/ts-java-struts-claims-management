package com.northstar.payment.web;

import java.util.List;
import java.util.Map;

/**
 * What a payment screen carries: the legacy forward name, the business
 * fields the JSP's {@code <ns:field>} tags would print, and the validation
 * errors. PAY-R08: every payment mapping is {@code validate="false"} and the
 * form's validate is empty, so {@code errors} is always empty.
 *
 * <p>PAY-R05 (v2) is the one exception: an amount above the claim's latest
 * settlement amount answers the issue screen with no fields and the single
 * validation key {@code payment.amount.exceedsSettlement}
 * (docs/changes/CHG-002-payment-exceeds-settlement.md).
 */
public record PaymentResponse(String screen, Map<String, String> fields,
        List<String> errors) {

    public static PaymentResponse of(String screen, Map<String, String> fields) {
        return new PaymentResponse(screen, fields, List.of());
    }

    /** PAY-R05 (v2): a screen with no business fields and one validation key. */
    public static PaymentResponse validationError(String screen, String key) {
        return new PaymentResponse(screen, Map.of(), List.of(key));
    }
}
