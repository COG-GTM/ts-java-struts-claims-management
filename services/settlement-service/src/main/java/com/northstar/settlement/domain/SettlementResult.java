package com.northstar.settlement.domain;

/**
 * Output of {@link SettlementCalculator}, mirroring the five fields the legacy
 * {@code Settlement} bean carries out of the calculator (SETTLE-R07, SETTLE-R42).
 *
 * <p>Values stay {@code double}: the legacy bean is double-typed and the display
 * formatter, the persistence layer and the rounding rule all depend on that
 * representation (SETTLE-R28, SETTLE-R31).
 *
 * @param coveredAmount     raw parsed covered amount, unrounded (SETTLE-R31)
 * @param deductibleApplied full parsed deductible, even if floored away (SETTLE-R30)
 * @param depreciation      raw parsed depreciation, unrounded (SETTLE-R31)
 * @param cappedAtLimit     strictly-greater-than comparison against the limit (SETTLE-R24)
 * @param settlementAmount  the single rounded figure (SETTLE-R28, SETTLE-R32)
 */
public record SettlementResult(
        double coveredAmount,
        double deductibleApplied,
        double depreciation,
        boolean cappedAtLimit,
        double settlementAmount) {
}
