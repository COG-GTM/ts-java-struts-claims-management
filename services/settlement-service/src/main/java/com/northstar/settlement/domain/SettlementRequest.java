package com.northstar.settlement.domain;

/**
 * The raw inbound contract of {@code /settlement/calculate} and {@code /settlement/save}.
 *
 * <p>Deliberately string-typed, like the Struts {@code SettlementForm}: the legacy
 * actions read {@code request.getParameter} and apply their own coercions
 * (SETTLE-R05, SETTLE-R11..R13, SETTLE-R16). A typed DTO would reject or convert
 * the blank and garbage inputs the transcripts depend on. {@code policyLimit} is
 * accepted but ignored (SETTLE-R06).
 */
public record SettlementRequest(
        String claimId,
        String coveredAmount,
        String deductible,
        String depreciation,
        String policyLimit) {

    public int resolvedClaimId() {
        return LegacyCoercions.integer(claimId, LegacyCoercions.DEFAULT_CLAIM_ID);
    }

    public double resolvedCoveredAmount() {
        return LegacyCoercions.decimal(coveredAmount, LegacyCoercions.DEFAULT_COVERED_AMOUNT);
    }

    public double resolvedDepreciation() {
        return LegacyCoercions.decimal(depreciation, LegacyCoercions.DEFAULT_DEPRECIATION);
    }

    public String resolvedDeductible() {
        return LegacyCoercions.deductible(deductible);
    }
}
