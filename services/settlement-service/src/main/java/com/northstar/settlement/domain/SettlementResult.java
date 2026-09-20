package com.northstar.settlement.domain;

import java.math.BigDecimal;

public record SettlementResult(BigDecimal coveredAmount,
        BigDecimal deductibleApplied, BigDecimal depreciation,
        boolean cappedAtLimit, BigDecimal settlementAmount) {
}
