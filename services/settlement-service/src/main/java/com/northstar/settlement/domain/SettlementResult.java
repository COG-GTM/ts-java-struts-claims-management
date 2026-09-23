package com.northstar.settlement.domain;

/** The legacy Settlement value object minus its identity columns, on double. */
public record SettlementResult(double coveredAmount, double deductibleApplied,
        double depreciation, boolean cappedAtLimit, double settlementAmount) {
}
