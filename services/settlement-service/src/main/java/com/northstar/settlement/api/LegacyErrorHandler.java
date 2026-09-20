package com.northstar.settlement.api;

import com.northstar.settlement.domain.ClaimNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Stand-in for the {@code <global-exceptions>} entry in {@code struts-config.xml}
 * that routed every {@code java.lang.Exception} to {@code /WEB-INF/jsp/error.jsp}
 * with HTTP 200 (SETTLE-R18, SETTLE-R15).
 *
 * <p>Legacy-faithful: the status is 200, not 4xx/5xx, because that is what the
 * running monolith returns for a non-numeric deductible. Listed in
 * docs/KNOWN_LEGACY_QUIRKS.md as a candidate for a separate business decision.
 */
@RestControllerAdvice
public class LegacyErrorHandler {

    /** SETTLE-R18: non-numeric deductible has no fallback and reaches the global handler. */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ScreenResponse<Map<String, String>>> numberFormat(NumberFormatException failure) {
        return errorScreen("errors.system", failure.getMessage());
    }

    /** SETTLE-R15: save with an unknown claim or policy failed on a null dereference. */
    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ScreenResponse<Map<String, String>>> claimNotFound(ClaimNotFoundException failure) {
        return errorScreen("errors.system", failure.getMessage());
    }

    private static ResponseEntity<ScreenResponse<Map<String, String>>> errorScreen(String key, String detail) {
        return ResponseEntity.ok(new ScreenResponse<>(
                ScreenResponse.ERROR_JSP, Map.of(), List.of(),
                Map.of("errorKey", key, "detail", detail == null ? "" : detail)));
    }
}
