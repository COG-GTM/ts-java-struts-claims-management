package com.northstar.settlement.calc;

/**
 * One settlement as produced by {@link SettlementCalculator} or read back from the database.
 *
 * <p>The five calculated members are the business fields the calculate screen renders
 * (SETTLE-R07); {@code settlementId}, {@code claimId}, {@code calculatedBy} and
 * {@code calculatedDate} are set only on the save and detail paths (SETTLE-R40, SETTLE-R41).
 */
public record Settlement(
        Integer settlementId,
        Integer claimId,
        double coveredAmount,
        double deductibleApplied,
        double depreciation,
        boolean cappedAtLimit,
        double settlementAmount,
        String calculatedBy,
        String calculatedDate) {

    public Settlement withSaveMetadata(int newSettlementId, int newClaimId, String operator, String date) {
        return new Settlement(newSettlementId, newClaimId, coveredAmount, deductibleApplied,
                depreciation, cappedAtLimit, settlementAmount, operator, date);
    }
}
