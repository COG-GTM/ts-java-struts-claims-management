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
 * (src/main/webapp/WEB-INF/struts-config.xml:59-60), as ADR-002 maps it: the
 * error screen with no business fields and no validation errors, in the 5xx
 * status class.
 *
 * <ul>
 *   <li>PAY-R04 — a claim with no settlement
 *       ({@link MissingSettlementException});</li>
 *   <li>PAY-R07 — a missing {@code payeeName} or {@code paymentMethod} breaks
 *       the NOT NULL constraint on insert ({@link DataAccessException}), as it
 *       does in the legacy DAO; the service adds no validation of its own;</li>
 *   <li>PAY-R22 — a detail request for an unknown payment id
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
