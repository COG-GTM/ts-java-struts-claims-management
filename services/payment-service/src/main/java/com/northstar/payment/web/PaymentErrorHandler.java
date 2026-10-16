package com.northstar.payment.web;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * PAY-R29: the equivalent of the monolith's global {@code java.lang.Exception}
 * mapping to error.jsp (src/main/webapp/WEB-INF/struts-config.xml:59-60,
 * web.xml:85-88), as ADR-002 maps it: {@code screen} "error", no business
 * fields, no validation errors, in the 5xx status class instead of the
 * forwarded 200.
 *
 * <ul>
 *   <li>PAY-R05 — a claim with no settlement ({@link MissingSettlementException});</li>
 *   <li>PAY-R12 — a missing or over-long {@code payeeName} / {@code paymentMethod}
 *       is rejected by the NOT NULL / VARCHAR constraints of
 *       src/main/resources/db/schema.sql:110,112 on insert
 *       ({@link DataAccessException}); the service adds no validation of its own
 *       (PAY-R11);</li>
 *   <li>PAY-R23 — a detail request for an unknown payment id
 *       ({@link MissingPaymentException}).</li>
 * </ul>
 */
@RestControllerAdvice
public class PaymentErrorHandler {

    @ExceptionHandler({MissingSettlementException.class, MissingPaymentException.class,
            DataAccessException.class})
    public ResponseEntity<PaymentResponse> error(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new PaymentResponse("error", Map.of(), List.of()));
    }
}
