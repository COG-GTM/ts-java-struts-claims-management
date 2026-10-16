package com.northstar.payment.web;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The equivalent of the monolith's global exception mapping to error.jsp
 * (src/main/webapp/WEB-INF/struts-config.xml:59-60).
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R10 — a missing payee or method (NOT NULL) or a method longer
 *       than VARCHAR(20) fails the insert and answers the error screen with no
 *       row written (src/main/resources/db/schema.sql:106-118; live L15, L16, L17).</li>
 *   <li>PAY-R11 — a claim with no settlement answers the error screen and
 *       writes nothing (PaymentIssueAction.java:23,28; live L18).</li>
 *   <li>PAY-R17 — an unknown paymentId answers the error screen
 *       (PaymentDetailAction.java:18-20; live L29).</li>
 * </ul>
 * Per ADR-002 ("How parity is judged") parity is on the status class, so the
 * Struts-forwarded 200 becomes 500 here; the screen and the empty field set are
 * unchanged. Exceptions that Spring resolves to a status of their own are left
 * to it: the monolith has no equivalent of them to reproduce.
 */
@RestControllerAdvice
public class PaymentErrorHandler {
    @ExceptionHandler({MissingSettlementException.class, MissingPaymentException.class,
            DataAccessException.class})
    public ResponseEntity<PaymentResponse> error(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PaymentResponse.error());
    }
}
