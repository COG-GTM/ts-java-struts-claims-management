package com.northstar.settlement.api;

import com.northstar.settlement.domain.ClaimNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Stand-in for the {@code <global-exceptions>} entry in {@code struts-config.xml}
 * that routed every {@code java.lang.Exception} to {@code /WEB-INF/jsp/error.jsp}
 * with HTTP 200 (SETTLE-R15; SETTLE-R18 on the save route, which CHG-001 leaves as
 * current state, OQ-15 a). Scoped to {@link SettlementController} so
 * that, like the Struts mapping, it only covers the module's own actions and leaves
 * framework responses (404, 405) alone.
 *
 * <p>Legacy-faithful: the status is 200, not 4xx/5xx, because that is what the
 * running monolith returns for a failed action. Listed in docs/KNOWN_LEGACY_QUIRKS.md;
 * the calculate route no longer reaches it for a bad deductible (SETTLE-R18 v2).
 */
@RestControllerAdvice(assignableTypes = SettlementController.class)
public class LegacyErrorHandler {

    /** SETTLE-R18 (save route): non-numeric deductible has no fallback and reaches the global handler. */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ScreenResponse<Map<String, String>>> numberFormat(NumberFormatException failure) {
        return errorScreen("errors.system", failure.getMessage());
    }

    /** SETTLE-R15: save with an unknown claim or policy failed on a null dereference. */
    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ScreenResponse<Map<String, String>>> claimNotFound(ClaimNotFoundException failure) {
        return errorScreen("errors.system", failure.getMessage());
    }

    /**
     * The legacy DAOs threw {@code SQLException} out of the action into the same global
     * handler (SETTLE-R15, SETTLE-R39: a duplicate id from the unlocked {@code max+1}
     * allocation surfaced as error.jsp, not HTTP 500). The SQL text is not echoed.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ScreenResponse<Map<String, String>>> dataAccess(DataAccessException failure) {
        return errorScreen("errors.system", failure.getClass().getSimpleName());
    }

    private static ResponseEntity<ScreenResponse<Map<String, String>>> errorScreen(String key, String detail) {
        return ResponseEntity.ok(new ScreenResponse<>(
                ScreenResponse.ERROR_JSP, Map.of(), List.of(),
                Map.of("errorKey", key, "detail", detail == null ? "" : detail)));
    }
}
