package com.northstar.settlement.api;

import java.util.List;
import java.util.Map;

/**
 * What the legacy JSP rendered, without the HTML. {@code screen} is the JSP
 * path relative to {@code /WEB-INF/jsp/} without the extension, {@code fields}
 * carries the same names as the legacy {@code <span id="f_...">} elements and
 * {@code errors} carries the message keys the legacy {@code ns:error}
 * markers would have carried.
 */
public record ScreenResponse(String screen, Map<String, String> fields,
        List<String> errors) {

    public static ScreenResponse of(String screen, Map<String, String> fields) {
        return new ScreenResponse(screen, fields, List.of());
    }
}
