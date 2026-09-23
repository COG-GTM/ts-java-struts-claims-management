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
 * <p>Implements SPEC-SETTLE-001 SETTLE-R26: a save against a missing claim or
 * policy produces the error screen with no business fields and no validation
 * errors (src/main/java/com/northstar/claims/web/SettlementSaveAction.java:25-33).
 * Per ADR-001 ("How parity is judged") parity is on the status class, so the
 * Struts-forwarded 200 becomes 500 here; the screen and fields are unchanged.
 *
 * <p>SETTLE-R06 (v1) sent a non-numeric deductible down this same path. Under
 * SETTLE-R06 (v2) the controller rejects it before the calculator, so it is a
 * validation response rather than an error screen
 * (docs/changes/CHG-001-invalid-deductible.md); the
 * {@link NumberFormatException} mapping stays as the backstop for any other
 * unparseable value the legacy would have thrown on.
 */
@RestControllerAdvice
public class SettlementErrorHandler {

    @ExceptionHandler({NumberFormatException.class, MissingClaimException.class})
    public ResponseEntity<SettlementResponse> error(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SettlementResponse("error", Map.of(), List.of()));
    }
}
