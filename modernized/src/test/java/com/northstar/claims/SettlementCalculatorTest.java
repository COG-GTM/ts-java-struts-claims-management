package com.northstar.claims;

import com.northstar.claims.service.SettlementCalculator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettlementCalculatorTest {
    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    void preservesOrderBlankDeductibleAndDoubleRounding() {
        assertEquals(4500, calculator.calculate(5000, "", 500, 10000).getSettlementAmount());
        assertEquals(4500, calculator.calculate(5000, null, 500, 10000).getSettlementAmount());
        assertEquals(1.0, calculator.calculate(1.005, "", 0, 10000).getSettlementAmount());
        var capped = calculator.calculate(20000, "100", 0, 10000);
        assertEquals(10000, capped.getSettlementAmount());
        assertTrue(capped.isCappedAtLimit());
        assertEquals(0, calculator.calculate(1000, "2000", 0, 10000).getSettlementAmount());
        assertEquals(4000, calculator.calculate(20000, "1000", 15000, 10000).getSettlementAmount());
        assertThrows(NumberFormatException.class, () -> calculator.calculate(1, "invalid", 0, 10));
    }
}
