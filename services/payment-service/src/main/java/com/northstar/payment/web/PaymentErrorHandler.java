package com.northstar.payment.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The equivalent of the global exception mapping to error.jsp
 * (src/main/webapp/WEB-INF/struts-config.xml:59-60). The legacy forward
 * answers 200; per ADR-002 ("How parity is judged") the service answers the
 * same error class as a 500 with screen {@code error} and no fields. Failed
 * inserts (NOT NULL, VARCHAR length) arrive as DataAccessException and write
 * nothing, as in the monolith.
 */
@RestControllerAdvice
public class PaymentErrorHandler {
    @ExceptionHandler({PaymentFailure.class, DataAccessException.class})
    public ResponseEntity<PaymentResponse> error(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PaymentResponse.error());
    }
}
