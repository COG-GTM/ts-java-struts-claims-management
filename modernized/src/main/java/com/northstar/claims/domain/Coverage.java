package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class Coverage {

    private static final Log log = LogFactory.getLog(Coverage.class);

    private int coverageId;
    private int policyId;
    private String coverageCode;
    private String description;
    private double coverageLimit;
    private double deductible;

    /** Returns the coverageId value. */
    public int getCoverageId() {
        return coverageId;
    }

    /** Stores the coverageId value supplied by the caller. */
    public void setCoverageId(int coverageId) {
        this.coverageId = coverageId;
    }

    /** Returns the policyId value. */
    public int getPolicyId() {
        return policyId;
    }

    /** Stores the policyId value supplied by the caller. */
    public void setPolicyId(int policyId) {
        this.policyId = policyId;
    }

    /** Returns the coverageCode value. */
    public String getCoverageCode() {
        return coverageCode;
    }

    /** Stores the coverageCode value supplied by the caller. */
    public void setCoverageCode(String coverageCode) {
        this.coverageCode = coverageCode;
    }

    /** Returns the description value. */
    public String getDescription() {
        return description;
    }

    /** Stores the description value supplied by the caller. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Returns the coverageLimit value. */
    public double getCoverageLimit() {
        return coverageLimit;
    }

    /** Stores the coverageLimit value supplied by the caller. */
    public void setCoverageLimit(double coverageLimit) {
        this.coverageLimit = coverageLimit;
    }

    /** Returns the deductible value. */
    public double getDeductible() {
        return deductible;
    }

    /** Stores the deductible value supplied by the caller. */
    public void setDeductible(double deductible) {
        this.deductible = deductible;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("Coverage[");
        text.append("coverageId=").append(coverageId);        text.append(", ");
        text.append("policyId=").append(policyId);        text.append(", ");
        text.append("coverageCode=").append(coverageCode);        text.append(", ");
        text.append("description=").append(description);        text.append(", ");
        text.append("coverageLimit=").append(coverageLimit);        text.append(", ");
        text.append("deductible=").append(deductible);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
