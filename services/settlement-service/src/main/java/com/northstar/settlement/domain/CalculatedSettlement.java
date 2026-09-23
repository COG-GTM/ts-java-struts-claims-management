package com.northstar.settlement.domain;

/**
 * Result of a settlement calculation.
 *
 * <p>Implements SPEC-SETTLE-001 SETTLE-R18: the calculator's result carries
 * {@code coveredAmount}, {@code deductibleApplied}, {@code depreciation},
 * {@code cappedAtLimit} and {@code settlementAmount}, with {@code claimId} set
 * by the caller afterwards
 * (src/main/java/com/northstar/claims/service/SettlementCalculator.java:38-45;
 * src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:38-42).
 *
 * <p>SETTLE-R17: only {@code settlementAmount} is rounded; the other amounts
 * are the values as supplied
 * (src/main/java/com/northstar/claims/service/SettlementCalculator.java:38-43).
 */
public record CalculatedSettlement(
        double coveredAmount,
        double deductibleApplied,
        double depreciation,
        boolean cappedAtLimit,
        double settlementAmount) {
}
