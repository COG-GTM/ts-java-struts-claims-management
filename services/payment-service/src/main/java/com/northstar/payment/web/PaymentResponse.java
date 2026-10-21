package com.northstar.payment.web;

import java.util.List;
import java.util.Map;

/**
 * One payment screen as data: the screen the legacy request forwards to, the
 * {@code <ns:field>} values in screen order keyed by their names, the
 * {@code <bean:write>} texts that have no field id (payee and method), the
 * links history.jsp prints, the validation keys (always empty: every payment
 * mapping has {@code validate="false"}) and the operator shown in the screen
 * audit table. Per ADR-002 the markup itself is not reproduced.
 */
public record PaymentResponse(String screen, Map<String, String> fields,
        Map<String, String> display, List<String> links, List<String> errors,
        String operator) {

    public static PaymentResponse error() {
        return new PaymentResponse("error", Map.of(), Map.of(), List.of(), List.of(), null);
    }
}
