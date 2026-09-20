package com.northstar.settlement.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The Struts application declares a global exception mapping for
 * {@code java.lang.Exception} that forwards to {@code error.jsp} with
 * HTTP 200 (struts-config.xml, {@code <global-exceptions>}). SETTLE-R06.
 */
@RestControllerAdvice(assignableTypes = SettlementController.class)
class LegacyErrorHandler {

    @ExceptionHandler(Exception.class)
    ScreenResponse systemError(Exception failure) {
        return new ScreenResponse("error", Map.of(), List.of("errors.system"));
    }
}
