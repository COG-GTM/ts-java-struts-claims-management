package com.northstar.settlement.api;

import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The Struts application declares a global exception mapping for
 * {@code java.lang.Exception} that forwards to {@code error.jsp} with
 * HTTP 200 (struts-config.xml, {@code <global-exceptions>}). SETTLE-R06 v2.
 * error.jsp carries no {@code ns:error} markers, so the response has no
 * validation errors either: the {@code error} screen is the whole signal
 * (transcripts/settlement_bad_deductible.json).
 */
@RestControllerAdvice(assignableTypes = SettlementController.class)
class LegacyErrorHandler {

    @ExceptionHandler(Exception.class)
    ScreenResponse systemError(Exception failure) {
        return ScreenResponse.of("error", Map.of());
    }
}
