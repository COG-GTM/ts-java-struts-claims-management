package com.northstar.settlement.web;

import java.util.List;
import java.util.Map;

/**
 * The response shape of the service: the screen the legacy request forwarded
 * to, the business field values keyed by their {@code <span id="f_NAME">} names,
 * and the validation errors.
 *
 * <p>Implements SPEC-SETTLE-001 SETTLE-R09: no Struts validation runs on the
 * settlement actions ({@code validate="false"}), so {@code errors} is always
 * empty on these paths
 * (src/main/webapp/WEB-INF/struts-config.xml:206-222). Per ADR-001 the Struts
 * forward path becomes the response shape rather than a view name.
 */
public record SettlementResponse(String screen, Map<String, String> fields,
        List<String> errors) {

    public static SettlementResponse of(String screen, Map<String, String> fields) {
        return new SettlementResponse(screen, fields, List.of());
    }
}
