package com.northstar.payment.web;

import java.util.List;
import java.util.Map;

/**
 * What a payment screen carries: the legacy forward name, the business
 * fields the JSP's {@code <ns:field>} tags would print, and the validation
 * errors. PAY-R08: every payment mapping is {@code validate="false"} and the
 * form's validate is empty, so {@code errors} is always empty.
 */
public record PaymentResponse(String screen, Map<String, String> fields,
        List<String> errors) {

    public static PaymentResponse of(String screen, Map<String, String> fields) {
        return new PaymentResponse(screen, fields, List.of());
    }
}
