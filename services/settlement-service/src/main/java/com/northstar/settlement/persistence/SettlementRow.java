package com.northstar.settlement.persistence;

import java.time.LocalDate;

/**
 * One {@code SETTLEMENT} row, matching the legacy {@code Settlement} bean column
 * for column (SETTLE-R42). Money columns are doubles because the legacy DAO
 * writes and reads them with {@code setDouble}/{@code getDouble}.
 */
public record SettlementRow(
        int settlementId,
        int claimId,
        double coveredAmount,
        double deductibleApplied,
        double depreciation,
        boolean cappedAtLimit,
        double settlementAmount,
        String calculatedBy,
        LocalDate calculatedDate) {
}
