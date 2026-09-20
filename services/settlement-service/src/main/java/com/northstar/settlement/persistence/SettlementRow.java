package com.northstar.settlement.persistence;

import java.math.BigDecimal;

/** One SETTLEMENT row, column for column. */
public record SettlementRow(int settlementId, int claimId,
        BigDecimal coveredAmount, BigDecimal deductibleApplied,
        BigDecimal depreciation, boolean cappedAtLimit,
        BigDecimal settlementAmount, String calculatedBy,
        String calculatedDate) {
}
