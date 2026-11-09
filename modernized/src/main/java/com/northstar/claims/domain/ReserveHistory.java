package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class ReserveHistory {

    private static final Log log = LogFactory.getLog(ReserveHistory.class);

    private int historyId;
    private int claimId;
    private double oldAmount;
    private double newAmount;
    private String changedBy;
    private String changedDate;
    private String reason;

    /** Returns the historyId value. */
    public int getHistoryId() {
        return historyId;
    }

    /** Stores the historyId value supplied by the caller. */
    public void setHistoryId(int historyId) {
        this.historyId = historyId;
    }

    /** Returns the claimId value. */
    public int getClaimId() {
        return claimId;
    }

    /** Stores the claimId value supplied by the caller. */
    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    /** Returns the oldAmount value. */
    public double getOldAmount() {
        return oldAmount;
    }

    /** Stores the oldAmount value supplied by the caller. */
    public void setOldAmount(double oldAmount) {
        this.oldAmount = oldAmount;
    }

    /** Returns the newAmount value. */
    public double getNewAmount() {
        return newAmount;
    }

    /** Stores the newAmount value supplied by the caller. */
    public void setNewAmount(double newAmount) {
        this.newAmount = newAmount;
    }

    /** Returns the changedBy value. */
    public String getChangedBy() {
        return changedBy;
    }

    /** Stores the changedBy value supplied by the caller. */
    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    /** Returns the changedDate value. */
    public String getChangedDate() {
        return changedDate;
    }

    /** Stores the changedDate value supplied by the caller. */
    public void setChangedDate(String changedDate) {
        this.changedDate = changedDate;
    }

    /** Returns the reason value. */
    public String getReason() {
        return reason;
    }

    /** Stores the reason value supplied by the caller. */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("ReserveHistory[");
        text.append("historyId=").append(historyId);        text.append(", ");
        text.append("claimId=").append(claimId);        text.append(", ");
        text.append("oldAmount=").append(oldAmount);        text.append(", ");
        text.append("newAmount=").append(newAmount);        text.append(", ");
        text.append("changedBy=").append(changedBy);        text.append(", ");
        text.append("changedDate=").append(changedDate);        text.append(", ");
        text.append("reason=").append(reason);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
