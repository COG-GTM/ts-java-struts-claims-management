package com.northstar.claims;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.northstar.claims.model.Settlement;
import com.northstar.claims.service.SettlementCalculator;

/**
 * Characterisation of the legacy settlement arithmetic against the captured
 * transcripts under transcripts/. Amounts are compared as the strings the UI
 * renders, because FieldTag formats money with String.format("%.2f", value).
 */
public class SettlementCharacterizationTest {

    /** Policy limit of policy 9001, the policy of claim 119 (seed.sql). */
    private static final double LIMIT_CLAIM_119 = 1000;

    /** Policy limit of policy 9002, the policy of claim 120 (seed.sql). */
    private static final double LIMIT_CLAIM_120 = 100000;

    private final SettlementCalculator calculator =
            SettlementCalculator.getInstance();

    private static String money(double value) {
        return String.format("%.2f", value);
    }

    private static void assertSettlement(String settlementAmount,
            String deductibleApplied, boolean cappedAtLimit, Settlement result) {
        assertEquals(settlementAmount, money(result.getSettlementAmount()));
        assertEquals(deductibleApplied, money(result.getDeductibleApplied()));
        assertEquals(cappedAtLimit, result.isCappedAtLimit());
    }

    /**
     * SETTLE-R08, SETTLE-R09, SETTLE-R11, SETTLE-R13: transcript
     * settlement_calculate. Claim 119 has a policy limit of 1000, so the net
     * 4500.00 is capped.
     */
    @Test
    public void settlementCalculate() {
        Settlement result =
                calculator.calculate(5000.00, "500.00", 0.00, LIMIT_CLAIM_119);
        assertSettlement("1000.00", "500.00", true, result);
    }

    /**
     * SETTLE-R06, SETTLE-R08, SETTLE-R11: transcript
     * settlement_blank_deductible. A blank deductible counts as zero and the
     * net stays below the 100000 limit of claim 120.
     */
    @Test
    public void settlementBlankDeductible() {
        Settlement result =
                calculator.calculate(5000.00, "", 500.00, LIMIT_CLAIM_120);
        assertSettlement("4500.00", "0.00", false, result);
    }

    /**
     * SETTLE-R10, SETTLE-R13: transcript settlement_deductible_floor. The net
     * is floored at zero while the full deductible is still reported.
     */
    @Test
    public void settlementDeductibleFloor() {
        Settlement result =
                calculator.calculate(1000.00, "2000.00", 0.00, LIMIT_CLAIM_120);
        assertSettlement("0.00", "2000.00", false, result);
    }

    /**
     * SETTLE-R12, SETTLE-R17: transcript settlement_half_cent. Double
     * arithmetic rounds 1.005 down to 1.00 even though the covered amount is
     * displayed as 1.01.
     */
    @Test
    public void settlementHalfCent() {
        Settlement result =
                calculator.calculate(1.005, "", 0.00, LIMIT_CLAIM_120);
        assertSettlement("1.00", "0.00", false, result);
        assertEquals("1.01", money(result.getCoveredAmount()));
    }

    /**
     * SETTLE-R11: transcript settlement_policy_cap. The net 19900.00 exceeds
     * the 1000 limit of claim 119, so the amount becomes the limit.
     */
    @Test
    public void settlementPolicyCap() {
        Settlement result =
                calculator.calculate(20000.00, "100.00", 0.00, LIMIT_CLAIM_119);
        assertSettlement("1000.00", "100.00", true, result);
    }

    /**
     * SETTLE-R07: transcript settlement_bad_deductible. A non-numeric
     * deductible throws out of the calculator, which the legacy web layer
     * turns into the error page.
     */
    @Test(expected = NumberFormatException.class)
    public void settlementBadDeductible() {
        calculator.calculate(5000.00, "abc", 0.00, LIMIT_CLAIM_119);
    }

    /**
     * SETTLE-R21, SETTLE-R25: transcript settlement_save. The save path
     * recalculates from the same parameters as settlement_calculate, so it
     * produces the same capped amount of 1000.00.
     */
    @Test
    public void settlementSave() {
        Settlement result =
                calculator.calculate(5000.00, "500.00", 0.00, LIMIT_CLAIM_119);
        assertSettlement("1000.00", "500.00", true, result);
    }
}
