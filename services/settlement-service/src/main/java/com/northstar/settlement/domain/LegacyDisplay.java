package com.northstar.settlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Two-decimal money display (SETTLE-R10). */
public final class LegacyDisplay {

    private LegacyDisplay() {
    }

    public static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
