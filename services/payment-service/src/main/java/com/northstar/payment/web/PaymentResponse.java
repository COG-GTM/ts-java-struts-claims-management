package com.northstar.payment.web;

import java.util.List;
import java.util.Map;

/**
 * The response shape of the service: the screen the legacy request forwarded
 * to, the business field values keyed by their {@code <ns:field name="...">}
 * names, the {@code <bean:write>} texts the payment JSPs print without a field
 * id (payee and method), the hard-coded links of history.jsp, and the
 * validation errors.
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R01 — issue answers {@code paymentAmount}, {@code checkNumber},
 *       {@code paymentStatus} (src/main/webapp/WEB-INF/jsp/payment/issue.jsp).</li>
 *   <li>PAY-R15 — {@code links} carries the two fixed detail links and their
 *       remittance links (src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19).</li>
 *   <li>PAY-R18 — {@code display} carries the payee and method texts, escaped
 *       as {@code <bean:write>} escapes them
 *       (src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14,18).</li>
 * </ul>
 * No Struts validation runs on the payment actions ({@code validate="false"},
 * src/main/webapp/WEB-INF/struts-config.xml:223-231), so {@code errors} is
 * empty on every legacy path. The one exception is the approved change CHG-002
 * (PAY-R05 (v2)): {@link #rejected} answers the issue screen with no fields and
 * the single key {@code payment.amount.aboveSettlement}. Per ADR-002 the Struts
 * forward path becomes the response shape.
 */
public record PaymentResponse(String screen, Map<String, String> fields,
        Map<String, String> display, List<String> links, List<String> errors) {
    public static PaymentResponse of(String screen, Map<String, String> fields) {
        return new PaymentResponse(screen, fields, Map.of(), List.of(), List.of());
    }

    public static PaymentResponse of(String screen, Map<String, String> fields,
            Map<String, String> display, List<String> links) {
        return new PaymentResponse(screen, fields, display, links, List.of());
    }

    /** A validation rejection: the screen the request came from, no fields, the keys. */
    public static PaymentResponse rejected(String screen, List<String> errors) {
        return new PaymentResponse(screen, Map.of(), Map.of(), List.of(), errors);
    }

    public static PaymentResponse error() {
        return new PaymentResponse("error", Map.of(), Map.of(), List.of(), List.of());
    }
}
