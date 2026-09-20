package com.northstar.settlement.api;

import java.util.List;
import java.util.Map;

/**
 * JSON replacement for a rendered Struts screen.
 *
 * <p>{@code fields} carries the business fields the JSP emitted as
 * {@code span} elements with ids {@code f_NAME}, already formatted the way the legacy
 * {@code ns:field} tag formatted them, because those strings are what the golden
 * transcripts compare (SETTLE-R07, SETTLE-R08, SETTLE-R34). {@code legacyForward}
 * names the JSP the monolith would have forwarded to so the replay harness can
 * compare the {@code result} line. {@code validationErrors} is always empty for
 * this module (SETTLE-R04). {@code data} is the typed payload for new consumers.
 *
 * @param <T> typed payload
 */
public record ScreenResponse<T>(
        String legacyForward,
        Map<String, String> fields,
        List<String> validationErrors,
        T data) {

    public static final String CALCULATE_JSP = "/WEB-INF/jsp/settlement/calculate.jsp";
    public static final String SAVE_JSP = "/WEB-INF/jsp/settlement/save.jsp";
    public static final String DETAIL_JSP = "/WEB-INF/jsp/settlement/detail.jsp";
    public static final String ERROR_JSP = "/WEB-INF/jsp/error.jsp";

    public static <T> ScreenResponse<T> of(String legacyForward, Map<String, String> fields, T data) {
        return new ScreenResponse<>(legacyForward, fields, List.of(), data);
    }
}
