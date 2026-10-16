package com.northstar.payment.web;

import java.util.List;
import java.util.Map;

/**
 * The service's rendering of a payment screen. {@code fields} carries exactly
 * the {@code ns:field} values the JSP would print (the parity surface, as in
 * ADR-001); {@code links} the in-page links the parity comparison checks for
 * history (PAY-R15); {@code display} the values the JSP prints with
 * {@code bean:write}, which are visible on screen but not fields (payee and
 * method on detail and remittance).
 */
public record PaymentResponse(String screen, Map<String, String> fields,
        List<String> errors, List<String> links, Map<String, String> display) {

    static PaymentResponse of(String screen, Map<String, String> fields) {
        return new PaymentResponse(screen, fields, List.of(), List.of(), Map.of());
    }
}
