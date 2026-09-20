package com.northstar.settlement.domain;

/** Legacy {@code Settlement} bean values, kept as {@code double} like the source. */
public record SettlementResult(double coveredAmount, double deductibleApplied,
        double depreciation, boolean cappedAtLimit, double settlementAmount) {
}
