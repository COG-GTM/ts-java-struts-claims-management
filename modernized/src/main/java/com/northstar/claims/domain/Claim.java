package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class Claim {

    private static final Log log = LogFactory.getLog(Claim.class);

    private int claimId;
    private int policyId;
    private String claimNumber;
    private String claimantName;
    private String lossDate;
    private String reportedDate;
    private String lossType;
    private String description;
    private String status;
    private double reserveAmount;
    private String assignedAdjuster;
    private String createdBy;
    private String createdDate;

    /** Returns the claimId value. */
    public int getClaimId() {
        return claimId;
    }

    /** Stores the claimId value supplied by the caller. */
    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    /** Returns the policyId value. */
    public int getPolicyId() {
        return policyId;
    }

    /** Stores the policyId value supplied by the caller. */
    public void setPolicyId(int policyId) {
        this.policyId = policyId;
    }

    /** Returns the claimNumber value. */
    public String getClaimNumber() {
        return claimNumber;
    }

    /** Stores the claimNumber value supplied by the caller. */
    public void setClaimNumber(String claimNumber) {
        this.claimNumber = claimNumber;
    }

    /** Returns the claimantName value. */
    public String getClaimantName() {
        return claimantName;
    }

    /** Stores the claimantName value supplied by the caller. */
    public void setClaimantName(String claimantName) {
        this.claimantName = claimantName;
    }

    /** Returns the lossDate value. */
    public String getLossDate() {
        return lossDate;
    }

    /** Stores the lossDate value supplied by the caller. */
    public void setLossDate(String lossDate) {
        this.lossDate = lossDate;
    }

    /** Returns the reportedDate value. */
    public String getReportedDate() {
        return reportedDate;
    }

    /** Stores the reportedDate value supplied by the caller. */
    public void setReportedDate(String reportedDate) {
        this.reportedDate = reportedDate;
    }

    /** Returns the lossType value. */
    public String getLossType() {
        return lossType;
    }

    /** Stores the lossType value supplied by the caller. */
    public void setLossType(String lossType) {
        this.lossType = lossType;
    }

    /** Returns the description value. */
    public String getDescription() {
        return description;
    }

    /** Stores the description value supplied by the caller. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Returns the status value. */
    public String getStatus() {
        return status;
    }

    /** Stores the status value supplied by the caller. */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Returns the reserveAmount value. */
    public double getReserveAmount() {
        return reserveAmount;
    }

    /** Stores the reserveAmount value supplied by the caller. */
    public void setReserveAmount(double reserveAmount) {
        this.reserveAmount = reserveAmount;
    }

    /** Returns the assignedAdjuster value. */
    public String getAssignedAdjuster() {
        return assignedAdjuster;
    }

    /** Stores the assignedAdjuster value supplied by the caller. */
    public void setAssignedAdjuster(String assignedAdjuster) {
        this.assignedAdjuster = assignedAdjuster;
    }

    /** Returns the createdBy value. */
    public String getCreatedBy() {
        return createdBy;
    }

    /** Stores the createdBy value supplied by the caller. */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /** Returns the createdDate value. */
    public String getCreatedDate() {
        return createdDate;
    }

    /** Stores the createdDate value supplied by the caller. */
    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("Claim[");
        text.append("claimId=").append(claimId);        text.append(", ");
        text.append("policyId=").append(policyId);        text.append(", ");
        text.append("claimNumber=").append(claimNumber);        text.append(", ");
        text.append("claimantName=").append(claimantName);        text.append(", ");
        text.append("lossDate=").append(lossDate);        text.append(", ");
        text.append("reportedDate=").append(reportedDate);        text.append(", ");
        text.append("lossType=").append(lossType);        text.append(", ");
        text.append("description=").append(description);        text.append(", ");
        text.append("status=").append(status);        text.append(", ");
        text.append("reserveAmount=").append(reserveAmount);        text.append(", ");
        text.append("assignedAdjuster=").append(assignedAdjuster);        text.append(", ");
        text.append("createdBy=").append(createdBy);        text.append(", ");
        text.append("createdDate=").append(createdDate);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
