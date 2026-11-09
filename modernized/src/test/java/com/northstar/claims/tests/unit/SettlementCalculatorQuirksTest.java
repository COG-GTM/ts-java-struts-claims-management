package com.northstar.claims.tests.unit;

import com.northstar.claims.domain.Settlement;
import com.northstar.claims.repository.SettlementDAO;
import com.northstar.claims.service.SettlementCalculator;
import com.northstar.claims.service.SettlementService;
import com.northstar.claims.support.FieldFormatter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Every MIG-14 settlement quirk, pinned so nobody "fixes" the legacy arithmetic. */
class SettlementCalculatorQuirksTest {
    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    void halfCentRoundsDownToOneDollarBecauseOfDoubleRepresentation() {
        // 1.005 is 1.00499999... as a double, so Math.round(x*100)/100 gives 1.00 not 1.01.
        Settlement result = calculator.calculate(1.005, "", 0, 10000);
        assertEquals(1.00, result.getSettlementAmount());
        assertEquals(1.0, calculator.round(1.005));
        // The ns money formatter rounds the covered amount the other way (1.01), as the legacy page shows.
        FieldFormatter ns = new FieldFormatter();
        assertEquals("<span id=\"f_coveredAmount\">1.01</span>", ns.field("coveredAmount", result.getCoveredAmount(), "money"));
        assertEquals("<span id=\"f_settlementAmount\">1.00</span>", ns.field("settlementAmount", result.getSettlementAmount(), "money"));
    }

    @Test
    void blankAndWhitespaceDeductibleAreZero() {
        assertEquals(4500, calculator.calculate(5000, "", 500, 10000).getSettlementAmount());
        assertEquals(4500, calculator.calculate(5000, "   ", 500, 10000).getSettlementAmount());
        assertEquals(0, calculator.calculate(5000, "", 500, 10000).getDeductibleApplied());
    }

    @Test
    void nullDeductibleIsZero() {
        Settlement result = calculator.calculate(5000, null, 500, 10000);
        assertEquals(4500, result.getSettlementAmount());
        assertEquals(0, result.getDeductibleApplied());
    }

    @Test
    void deductibleAboveLossFloorsAtZero() {
        Settlement result = calculator.calculate(1000, "2000", 0, 10000);
        assertEquals(0.00, result.getSettlementAmount());
        assertFalse(result.isCappedAtLimit());
        assertEquals(2000, result.getDeductibleApplied());
    }

    @Test
    void capAtPolicyLimitSetsCappedAtLimit() {
        Settlement capped = calculator.calculate(20000, "100", 0, 10000);
        assertEquals(10000, capped.getSettlementAmount());
        assertTrue(capped.isCappedAtLimit());
        // Exactly at the limit is not "capped": the comparison is strictly greater-than.
        Settlement atLimit = calculator.calculate(10100, "100", 0, 10000);
        assertEquals(10000, atLimit.getSettlementAmount());
        assertFalse(atLimit.isCappedAtLimit());
    }

    @Test
    void orderIsDepreciationThenDeductibleThenFloorThenCapThenRound() {
        // depreciation and deductible both come off before the cap: 20000-15000-1000 = 4000 < 10000.
        assertEquals(4000, calculator.calculate(20000, "1000", 15000, 10000).getSettlementAmount());
        // floor happens before the cap, so a negative never reaches the cap and a zero limit still yields 0.
        Settlement floored = calculator.calculate(100, "50", 100, 0);
        assertEquals(0, floored.getSettlementAmount());
        assertFalse(floored.isCappedAtLimit());
        // cap happens before rounding: the limit itself is rounded (1000.004 -> 1000.0).
        Settlement roundedCap = calculator.calculate(5000, "", 0, 1000.004);
        assertTrue(roundedCap.isCappedAtLimit());
        assertEquals(1000.0, roundedCap.getSettlementAmount());
        // rounding is last: 10.004 - 0.001 - 0.001 = 10.002 -> 10.0.
        assertEquals(10.0, calculator.calculate(10.004, "0.001", 0.001, 10000).getSettlementAmount());
        // the result echoes the raw inputs, not intermediate values.
        Settlement echo = calculator.calculate(20000, "1000", 15000, 10000);
        assertEquals(20000, echo.getCoveredAmount());
        assertEquals(15000, echo.getDepreciation());
    }

    @Test
    void nonNumericDeductibleThrowsLikeLegacy() {
        assertThrows(NumberFormatException.class, () -> calculator.calculate(1, "invalid", 0, 10));
    }

    @Test
    void closedClaimIsStillCalculableAtServiceLevel() throws Exception {
        // Seed claim 120 is CLOSED; the service never looks at claim status, so it calculates and saves.
        SettlementDAO dao = mock(SettlementDAO.class);
        SettlementService service = new SettlementService(dao, calculator);
        Settlement saved = service.calculateAndSave(120, 6000, "", 0, 100000);
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(dao).save(captor.capture());
        assertSame(saved, captor.getValue());
        assertEquals(6000, saved.getSettlementAmount());
        // Deterministic legacy identity: id = claimId + 10000, fixed operator and date.
        assertEquals(10120, saved.getSettlementId());
        assertEquals(120, saved.getClaimId());
        assertEquals("supervisor", saved.getCalculatedBy());
        assertEquals("2019-03-01", saved.getCalculatedDate());
    }
}
