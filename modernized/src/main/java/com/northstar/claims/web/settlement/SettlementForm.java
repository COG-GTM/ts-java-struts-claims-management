package com.northstar.claims.web.settlement;

/** String binding retains legacy parsing fallbacks rather than MVC conversion errors. */
public record SettlementForm(String claimId, String coveredAmount, String deductible,
        String depreciation, String policyLimit) {
}
