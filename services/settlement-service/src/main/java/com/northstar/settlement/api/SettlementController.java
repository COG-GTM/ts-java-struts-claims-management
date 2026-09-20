package com.northstar.settlement.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.northstar.settlement.domain.LegacyDisplay;
import com.northstar.settlement.domain.SettlementResult;
import com.northstar.settlement.domain.SettlementService;
import com.northstar.settlement.persistence.SettlementRow;

/**
 * Replaces SettlementCalculateAction, SettlementSaveAction and
 * SettlementDetailAction. Paths drop the {@code .do} suffix; parameter names
 * are unchanged (SETTLE-R01).
 */
@RestController
@RequestMapping("/settlement")
class SettlementController {

    private final SettlementService service;

    SettlementController(SettlementService service) {
        this.service = service;
    }

    @RequestMapping(value = "/calculate",
            method = { RequestMethod.GET, RequestMethod.POST })
    ScreenResponse calculate(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation) {
        SettlementResult result = service.calculate(claimId, coveredAmount,
                deductible, depreciation);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("coveredAmount", LegacyDisplay.money(result.coveredAmount()));
        fields.put("deductibleApplied",
                LegacyDisplay.money(result.deductibleApplied()));
        fields.put("depreciation", LegacyDisplay.money(result.depreciation()));
        fields.put("cappedAtLimit", String.valueOf(result.cappedAtLimit()));
        fields.put("settlementAmount",
                LegacyDisplay.money(result.settlementAmount()));
        return ScreenResponse.of("settlement/calculate", fields);
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    ScreenResponse save(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String coveredAmount,
            @RequestParam(required = false) String deductible,
            @RequestParam(required = false) String depreciation) {
        SettlementRow saved = service.save(claimId, coveredAmount, deductible,
                depreciation);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("settlementAmount",
                LegacyDisplay.money(saved.settlementAmount()));
        fields.put("savedBy", saved.calculatedBy());
        return ScreenResponse.of("settlement/save", fields);
    }

    @GetMapping("/detail")
    ScreenResponse detail(@RequestParam(required = false) String claimId) {
        SettlementRow row = service.detail(claimId);
        Map<String, String> fields = new LinkedHashMap<>();
        if (row != null) {
            fields.put("detailSettlementId", String.valueOf(row.settlementId()));
            fields.put("detailClaimId", String.valueOf(row.claimId()));
            fields.put("detailCoveredAmount",
                    LegacyDisplay.money(row.coveredAmount()));
            fields.put("detailDeductible",
                    LegacyDisplay.money(row.deductibleApplied()));
            fields.put("detailDepreciation",
                    LegacyDisplay.money(row.depreciation()));
            fields.put("detailCapped", String.valueOf(row.cappedAtLimit()));
            fields.put("detailAmount", LegacyDisplay.money(row.settlementAmount()));
            fields.put("detailDate", row.calculatedDate());
        }
        return ScreenResponse.of("settlement/detail", fields);
    }
}
