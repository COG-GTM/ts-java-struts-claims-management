package com.northstar.claims.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.domain.Settlement;

/** Calculates settlement values using the arithmetic rules used by adjusters. */
@org.springframework.stereotype.Service
public class SettlementCalculator {

    private final Log log = LogFactory.getLog(SettlementCalculator.class);

    public SettlementCalculator() {
    }


    /** Applies depreciation, deductible, policy cap, and cent rounding. */
    public Settlement calculate(double coveredAmount, String deductible,
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
        Settlement result = new Settlement();
        result.setCoveredAmount(coveredAmount);
        result.setDeductibleApplied(deductibleValue);
        result.setDepreciation(depreciation);
        result.setCappedAtLimit(capped);
        result.setSettlementAmount(rounded);
        log.debug("Settlement calculated: " + rounded);
        return result;
    }

    public double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
