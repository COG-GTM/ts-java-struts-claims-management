package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Locale;
import org.junit.Test;
import com.northstar.claims.model.Settlement;
import com.northstar.claims.service.SettlementCalculator;

/**
 * Characterisation tests for SPEC-SETTLE-001. Each test pins the value a
 * captured transcript in transcripts/ shows, using the inputs from that
 * transcript and the policy limit of the seeded claim. They describe what the
 * legacy code does, not what it should do.
 */
public class SettlementCharacterizationTest {

    private final SettlementCalculator calculator =
            SettlementCalculator.getInstance();

    /** Policy 9001 (claim 119) has policy_limit 1000 in seed.sql. */
    private static final double LIMIT_9001 = 1000;

    /** Policy 9002 (claim 120) has policy_limit 100000 in seed.sql. */
    private static final double LIMIT_9002 = 100000;

    private static String money(double value) {
        return String.format(Locale.US, "%.2f", new Object[] {
                new Double(value) });
    }

    /** transcripts/settlement_calculate.json, SETTLE-R07, SETTLE-R08. */
    @Test
    public void settlementCalculateIsCappedAtPolicyLimit() {
        Settlement result = calculator.calculate(5000.00, "500.00", 0.00,
                LIMIT_9001);
        assertEquals("1000.00", money(result.getSettlementAmount()));
        assertEquals("500.00", money(result.getDeductibleApplied()));
        assertTrue(result.isCappedAtLimit());
    }

    /** transcripts/settlement_blank_deductible.json, SETTLE-R04, SETTLE-R07. */
    @Test
    public void settlementBlankDeductibleIsZero() {
        Settlement result = calculator.calculate(5000.00, "", 500.00,
                LIMIT_9002);
        assertEquals("0.00", money(result.getDeductibleApplied()));
        assertEquals("4500.00", money(result.getSettlementAmount()));
        assertFalse(result.isCappedAtLimit());
    }

    /** transcripts/settlement_deductible_floor.json, SETTLE-R07. */
    @Test
    public void settlementDeductibleFloorIsZero() {
        Settlement result = calculator.calculate(1000.00, "2000.00", 0.00,
                LIMIT_9002);
        assertEquals("0.00", money(result.getSettlementAmount()));
        assertEquals("2000.00", money(result.getDeductibleApplied()));
    }

    /** transcripts/settlement_policy_cap.json, SETTLE-R08. */
    @Test
    public void settlementPolicyCapUsesLimit() {
        Settlement result = calculator.calculate(20000.00, "100.00", 0.00,
                LIMIT_9001);
        assertEquals("1000.00", money(result.getSettlementAmount()));
        assertTrue(result.isCappedAtLimit());
    }

    /** transcripts/settlement_half_cent.json, SETTLE-R09, SETTLE-R10. */
    @Test
    public void settlementHalfCentRoundsDownButDisplaysCoveredUp() {
        Settlement result = calculator.calculate(1.005, "0", 0.00, LIMIT_9002);
        assertEquals("1.00", money(result.getSettlementAmount()));
        assertEquals("1.01", money(result.getCoveredAmount()));
    }

    /** transcripts/settlement_bad_deductible.json, SETTLE-R06. */
    @Test(expected = NumberFormatException.class)
    public void settlementBadDeductibleThrows() {
        calculator.calculate(5000.00, "abc", 0.00, LIMIT_9001);
    }

    /** transcripts/settlement_save.json, SETTLE-R12: save recalculates. */
    @Test
    public void settlementSaveRecalculatesSameAmount() {
        Settlement result = calculator.calculate(5000.00, "500.00", 0.00,
                LIMIT_9001);
        assertEquals("1000.00", money(result.getSettlementAmount()));
    }
}
