package com.northstar.settlement.persistence;

/**
 * One SETTLEMENT row, with the nine columns of
 * src/main/resources/db/schema.sql:92-104 read by SettlementDAO.read
 * (src/main/java/com/northstar/claims/dao/SettlementDAO.java:145-157).
 *
 * <p>Implements SPEC-SETTLE-001 SETTLE-R28: the insert writes all nine columns.
 */
public record StoredSettlement(
        int settlementId,
        int claimId,
        double coveredAmount,
        double deductibleApplied,
        double depreciation,
        boolean cappedAtLimit,
        double settlementAmount,
        String calculatedBy,
        String calculatedDate) {
}
