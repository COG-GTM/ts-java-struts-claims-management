package com.northstar.settlement.domain;

import org.springframework.stereotype.Component;

/**
 * The legacy settlement arithmetic, reproduced statement for statement from
 * src/main/java/com/northstar/claims/service/SettlementCalculator.java:26-37.
 *
 * <p>Implements SPEC-SETTLE-001:
 * <ul>
 *   <li>SETTLE-R05 — a null or whitespace-only deductible counts as 0
 *       (SettlementCalculator.java:26-29).</li>
 *   <li>SETTLE-R06 — a non-empty, non-numeric deductible reaches
 *       {@link Double#parseDouble} unguarded and throws
 *       (SettlementCalculator.java:28).</li>
 *   <li>SETTLE-R10 — gross loss = coveredAmount - depreciation
 *       (SettlementCalculator.java:30).</li>
 *   <li>SETTLE-R11 — net = gross - deductible (SettlementCalculator.java:31).</li>
 *   <li>SETTLE-R12 — the net is floored at zero
 *       (SettlementCalculator.java:32-34).</li>
 *   <li>SETTLE-R13 — cappedAtLimit is true when the floored net is strictly
 *       greater than the policy limit, and the settlement becomes the limit
 *       (SettlementCalculator.java:35-36).</li>
 *   <li>SETTLE-R14 — order: depreciation, deductible, zero floor, cap, rounding
 *       (SettlementCalculator.java:30-37).</li>
 *   <li>SETTLE-R15 — rounding is the single statement
 *       {@code Math.round(amount * 100.0) / 100.0}, binary double arithmetic
 *       (SettlementCalculator.java:37).</li>
 *   <li>SETTLE-R16 — consequently 1.005 settles at 1.00
 *       (transcript settlement_half_cent).</li>
 *   <li>SETTLE-R17 — only the settlement amount is rounded
 *       (SettlementCalculator.java:38-43).</li>
 * </ul>
 */
@Component
public class SettlementCalculator {

    /** Applies depreciation, deductible, zero floor, policy cap, then rounding. */
    public CalculatedSettlement calculate(double coveredAmount, String deductible,
            double depreciation, double policyLimit) {
        double deductibleValue = 0;
        if (deductible != null && deductible.trim().length() > 0) {
            deductibleValue = Double.parseDouble(deductible);
        }
        double gross = coveredAmount - depreciation;
        double afterDeductible = gross - deductibleValue;
        if (afterDeductible < 0) {
            afterDeductible = 0;
        }
        boolean capped = afterDeductible > policyLimit;
        double amount = capped ? policyLimit : afterDeductible;
        double rounded = Math.round(amount * 100.0) / 100.0;
        return new CalculatedSettlement(coveredAmount, deductibleValue,
                depreciation, capped, rounded);
    }
}
