package com.northstar.settlement.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The Struts {@code global-exceptions} entry for {@code java.lang.Exception}: any failure
 * inside a settlement action answers HTTP 200 with the forward {@code /WEB-INF/jsp/error.jsp}
 * rather than a 500 (SETTLE-R18, SETTLE-R15).
 */
@RestControllerAdvice
public class LegacyFailureAdvice {

    static final String ERROR_VIEW = "/WEB-INF/jsp/error.jsp";

    /** The legacy error page carries no {@code ns:error} marker, so no validation key is emitted. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception failure) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ScreenRenderer(ERROR_VIEW).render());
    }
}
