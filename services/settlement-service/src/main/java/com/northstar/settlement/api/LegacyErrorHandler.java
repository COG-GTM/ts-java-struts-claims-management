package com.northstar.settlement.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.northstar.settlement.domain.InvalidDeductibleException;

/**
 * The Struts application declares a global exception mapping for
 * {@code java.lang.Exception} that forwards to {@code error.jsp} with
 * HTTP 200 (struts-config.xml, {@code <global-exceptions>}). SETTLE-R06.
 * {@code error.jsp} prints the {@code errors.system} message but emits no
 * {@code ns:error} marker, so the errors list stays empty and the screen
 * name alone says what happened (docs/KNOWN_LEGACY_QUIRKS.md, QUIRK-05).
 */
@RestControllerAdvice(assignableTypes = SettlementController.class)
class LegacyErrorHandler {

    @ExceptionHandler(Exception.class)
    ScreenResponse systemError(Exception failure) {
        return new ScreenResponse("error", Map.of(), List.of());
    }

    /** SETTLE-R06 v2 (CHG-001): stay on the calculate screen, name the field. */
    @ExceptionHandler(InvalidDeductibleException.class)
    ScreenResponse invalidDeductible(InvalidDeductibleException failure) {
        return new ScreenResponse("settlement/calculate", Map.of(),
                List.of(InvalidDeductibleException.KEY));
    }
}
