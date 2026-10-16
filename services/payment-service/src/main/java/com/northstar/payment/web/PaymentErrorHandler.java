package com.northstar.payment.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The equivalent of the monolith's global exception mapping to error.jsp
 * (src/main/webapp/WEB-INF/struts-config.xml:59-60), for the payment flows.
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R11 — issue for a claim with no settlement.</li>
 *   <li>PAY-R10 — issue with no payee, no method, or a method longer than
 *       20 characters: the insert fails and nothing is written.</li>
 *   <li>PAY-R17 — detail of a payment id that does not exist.</li>
 * </ul>
 * As ADR-002 records (following ADR-001), parity is on the status class, so
 * the Struts-forwarded error page becomes a 500 with screen "error" and no
 * fields.
 */
@RestControllerAdvice
public class PaymentErrorHandler {

    @ExceptionHandler({MissingPaymentDataException.class, DataAccessException.class})
    public ResponseEntity<PaymentResponse> error(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PaymentResponse.of("error", java.util.Map.of()));
    }
}
