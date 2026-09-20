package com.northstar.settlement.domain;

/**
 * What {@code SettlementCalculateAction} leaves in request scope: the calculator
 * output, the claim it was computed for, and the limit that was applied
 * (which the calculate screen never displays, SETTLE-R36).
 */
public record CalculatedSettlement(int claimId, double policyLimit, SettlementResult result) {
}
