package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class Settlement {

    private static final Log log = LogFactory.getLog(Settlement.class);

    private int settlementId;
    private int claimId;
    private double coveredAmount;
    private double deductibleApplied;
    private double depreciation;
    private boolean cappedAtLimit;
    private double settlementAmount;
    private String calculatedBy;
    private String calculatedDate;

    /** Returns the settlementId value. */
    public int getSettlementId() {
        return settlementId;
    }

    /** Stores the settlementId value supplied by the caller. */
    public void setSettlementId(int settlementId) {
        this.settlementId = settlementId;
    }

    /** Returns the claimId value. */
    public int getClaimId() {
        return claimId;
    }

    /** Stores the claimId value supplied by the caller. */
    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    /** Returns the coveredAmount value. */
    public double getCoveredAmount() {
        return coveredAmount;
    }

    /** Stores the coveredAmount value supplied by the caller. */
    public void setCoveredAmount(double coveredAmount) {
        this.coveredAmount = coveredAmount;
    }

    /** Returns the deductibleApplied value. */
    public double getDeductibleApplied() {
        return deductibleApplied;
    }

    /** Stores the deductibleApplied value supplied by the caller. */
    public void setDeductibleApplied(double deductibleApplied) {
        this.deductibleApplied = deductibleApplied;
    }

    /** Returns the depreciation value. */
    public double getDepreciation() {
        return depreciation;
    }

    /** Stores the depreciation value supplied by the caller. */
    public void setDepreciation(double depreciation) {
        this.depreciation = depreciation;
    }

    /** Returns the cappedAtLimit value. */
    public boolean isCappedAtLimit() {
        return cappedAtLimit;
    }

    /** Stores the cappedAtLimit value supplied by the caller. */
    public void setCappedAtLimit(boolean cappedAtLimit) {
        this.cappedAtLimit = cappedAtLimit;
    }

    /** Returns the settlementAmount value. */
    public double getSettlementAmount() {
        return settlementAmount;
    }

    /** Stores the settlementAmount value supplied by the caller. */
    public void setSettlementAmount(double settlementAmount) {
        this.settlementAmount = settlementAmount;
    }

    /** Returns the calculatedBy value. */
    public String getCalculatedBy() {
        return calculatedBy;
    }

    /** Stores the calculatedBy value supplied by the caller. */
    public void setCalculatedBy(String calculatedBy) {
        this.calculatedBy = calculatedBy;
    }

    /** Returns the calculatedDate value. */
    public String getCalculatedDate() {
        return calculatedDate;
    }

    /** Stores the calculatedDate value supplied by the caller. */
    public void setCalculatedDate(String calculatedDate) {
        this.calculatedDate = calculatedDate;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("Settlement[");
        text.append("settlementId=").append(settlementId);        text.append(", ");
        text.append("claimId=").append(claimId);        text.append(", ");
        text.append("coveredAmount=").append(coveredAmount);        text.append(", ");
        text.append("deductibleApplied=").append(deductibleApplied);        text.append(", ");
        text.append("depreciation=").append(depreciation);        text.append(", ");
        text.append("cappedAtLimit=").append(cappedAtLimit);        text.append(", ");
        text.append("settlementAmount=").append(settlementAmount);        text.append(", ");
        text.append("calculatedBy=").append(calculatedBy);        text.append(", ");
        text.append("calculatedDate=").append(calculatedDate);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
