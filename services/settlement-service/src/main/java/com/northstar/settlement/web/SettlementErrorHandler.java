package com.northstar.settlement.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The equivalent of the monolith's global exception mapping to error.jsp
 * (src/main/webapp/WEB-INF/struts-config.xml:59-60).
 *
 * <p>Implements SPEC-SETTLE-001 SETTLE-R06 and SETTLE-R26: a non-numeric
 * deductible, and a save against a missing claim or policy, produce the error
 * screen with no business fields and no validation errors — transcript
 * settlement_bad_deductible records
 * {@code forward:/WEB-INF/jsp/error.jsp} with empty {@code business_fields}.
 * Per ADR-001 ("How parity is judged") parity is on the status class, so the
 * Struts-forwarded 200 becomes 500 here; the screen and fields are unchanged.
 */
@RestControllerAdvice
public class SettlementErrorHandler {

    @ExceptionHandler({NumberFormatException.class, MissingClaimException.class})
    public ResponseEntity<SettlementResponse> error(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SettlementResponse("error", Map.of(), List.of()));
    }
}
