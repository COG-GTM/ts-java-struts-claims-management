package com.northstar.settlement.persistence;

/** One SETTLEMENT row, column for column, read and written as double like SettlementDAO. */
public record SettlementRow(int settlementId, int claimId,
        double coveredAmount, double deductibleApplied, double depreciation,
        boolean cappedAtLimit, double settlementAmount, String calculatedBy,
        String calculatedDate) {
}
