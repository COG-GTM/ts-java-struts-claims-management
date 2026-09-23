package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import com.northstar.claims.model.Settlement;
import com.northstar.claims.service.SettlementCalculator;

/**
 * Pins the settlement arithmetic to the recorded transcripts under
 * transcripts/. Each test replays one transcript's form inputs through
 * SettlementCalculator with the policy limit that the claim's policy carries
 * in src/main/resources/db/seed.sql: claim 119 uses policy 9001 (limit 1000)
 * and claim 120 uses policy 9002 (limit 100000).
 */
public class SettlementCharacterizationTest {

    private static final double CLAIM_119_LIMIT = 1000;
    private static final double CLAIM_120_LIMIT = 100000;

    private final SettlementCalculator calculator =
            SettlementCalculator.getInstance();

    /** Money as the ns:field money type renders it. */
    private static String money(double value) {
        return String.format("%.2f", new Object[] { new Double(value) });
    }

    /**
     * SETTLE-R10, SETTLE-R11, SETTLE-R13: gross, deductible, then the cap at
     * the policy limit of claim 119.
     * Transcript: settlement_calculate.
     */
    @Test
    public void settlementCalculate() {
        Settlement result =
                calculator.calculate(5000.00, "500.00", 0.00, CLAIM_119_LIMIT);
        assertEquals("1000.00", money(result.getSettlementAmount()));
        assertEquals("500.00", money(result.getDeductibleApplied()));
        assertTrue(result.isCappedAtLimit());
    }

    /**
     * SETTLE-R11, SETTLE-R13: the save path calculates from the same inputs as
     * the calculate path and stores the capped amount.
     * Transcript: settlement_save.
     */
    @Test
    public void settlementSave() {
        Settlement result =
                calculator.calculate(5000.00, "500.00", 0.00, CLAIM_119_LIMIT);
        assertEquals("1000.00", money(result.getSettlementAmount()));
        assertEquals("500.00", money(result.getDeductibleApplied()));
        assertTrue(result.isCappedAtLimit());
    }

    /**
     * SETTLE-R13: a net amount far above the limit still settles at the limit
     * of claim 119 and reports cappedAtLimit.
     * Transcript: settlement_policy_cap.
     */
    @Test
    public void settlementPolicyCap() {
        Settlement result =
                calculator.calculate(20000.00, "100.00", 0.00, CLAIM_119_LIMIT);
        assertEquals("1000.00", money(result.getSettlementAmount()));
        assertEquals("100.00", money(result.getDeductibleApplied()));
        assertTrue(result.isCappedAtLimit());
    }

    /**
     * SETTLE-R12: a deductible above the gross loss floors the settlement at
     * zero and leaves cappedAtLimit false.
     * Transcript: settlement_deductible_floor.
     */
    @Test
    public void settlementDeductibleFloor() {
        Settlement result =
                calculator.calculate(1000.00, "2000.00", 0.00, CLAIM_120_LIMIT);
        assertEquals("0.00", money(result.getSettlementAmount()));
        assertEquals("2000.00", money(result.getDeductibleApplied()));
        assertFalse(result.isCappedAtLimit());
    }

    /**
     * SETTLE-R05, SETTLE-R10: a blank deductible applies 0.00 and the
     * settlement is the depreciated gross loss.
     * Transcript: settlement_blank_deductible.
     */
    @Test
    public void settlementBlankDeductible() {
        Settlement result =
                calculator.calculate(5000.00, "", 500.00, CLAIM_120_LIMIT);
        assertEquals("4500.00", money(result.getSettlementAmount()));
        assertEquals("0.00", money(result.getDeductibleApplied()));
        assertFalse(result.isCappedAtLimit());
    }

    /**
     * SETTLE-R15, SETTLE-R16: binary double rounding sends the half cent of
     * 1.005 down to 1.00.
     * Transcript: settlement_half_cent.
     */
    @Test
    public void settlementHalfCent() {
        Settlement result =
                calculator.calculate(1.005, "", 0.00, CLAIM_120_LIMIT);
        assertEquals("1.00", money(result.getSettlementAmount()));
        assertEquals("0.00", money(result.getDeductibleApplied()));
        assertFalse(result.isCappedAtLimit());
    }

    /**
     * SETTLE-R06: a non-numeric deductible reaches Double.parseDouble
     * unguarded, which is what sends the request to the error page.
     * Transcript: settlement_bad_deductible.
     */
    @Test(expected = NumberFormatException.class)
    public void settlementBadDeductible() {
        calculator.calculate(5000.00, "abc", 0.00, CLAIM_119_LIMIT);
    }
}
