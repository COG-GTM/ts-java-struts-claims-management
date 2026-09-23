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
 *
 * <p>SETTLE-R06 (v2) is the one exception: an unparseable deductible answers
 * the calculate screen with no fields and the single validation key
 * {@code settlement.deductible.invalid}
 * (docs/changes/CHG-001-invalid-deductible.md).
 */
public record SettlementResponse(String screen, Map<String, String> fields,
        List<String> errors) {

    public static SettlementResponse of(String screen, Map<String, String> fields) {
        return new SettlementResponse(screen, fields, List.of());
    }

    /** SETTLE-R06 (v2): a screen with no business fields and one validation key. */
    public static SettlementResponse validationError(String screen, String key) {
        return new SettlementResponse(screen, Map.of(), List.of(key));
    }
}
