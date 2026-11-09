package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class Policy {

    private static final Log log = LogFactory.getLog(Policy.class);

    private int policyId;
    private String policyNumber;
    private String lineOfBusiness;
    private String insuredName;
    private String insuredAddress;
    private String effectiveDate;
    private String expiryDate;
    private double policyLimit;
    private double deductible;
    private double annualPremium;
    private String status;

    /** Returns the policyId value. */
    public int getPolicyId() {
        return policyId;
    }

    /** Stores the policyId value supplied by the caller. */
    public void setPolicyId(int policyId) {
        this.policyId = policyId;
    }

    /** Returns the policyNumber value. */
    public String getPolicyNumber() {
        return policyNumber;
    }

    /** Stores the policyNumber value supplied by the caller. */
    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    /** Returns the lineOfBusiness value. */
    public String getLineOfBusiness() {
        return lineOfBusiness;
    }

    /** Stores the lineOfBusiness value supplied by the caller. */
    public void setLineOfBusiness(String lineOfBusiness) {
        this.lineOfBusiness = lineOfBusiness;
    }

    /** Returns the insuredName value. */
    public String getInsuredName() {
        return insuredName;
    }

    /** Stores the insuredName value supplied by the caller. */
    public void setInsuredName(String insuredName) {
        this.insuredName = insuredName;
    }

    /** Returns the insuredAddress value. */
    public String getInsuredAddress() {
        return insuredAddress;
    }

    /** Stores the insuredAddress value supplied by the caller. */
    public void setInsuredAddress(String insuredAddress) {
        this.insuredAddress = insuredAddress;
    }

    /** Returns the effectiveDate value. */
    public String getEffectiveDate() {
        return effectiveDate;
    }

    /** Stores the effectiveDate value supplied by the caller. */
    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    /** Returns the expiryDate value. */
    public String getExpiryDate() {
        return expiryDate;
    }

    /** Stores the expiryDate value supplied by the caller. */
    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    /** Returns the policyLimit value. */
    public double getPolicyLimit() {
        return policyLimit;
    }

    /** Stores the policyLimit value supplied by the caller. */
    public void setPolicyLimit(double policyLimit) {
        this.policyLimit = policyLimit;
    }

    /** Returns the deductible value. */
    public double getDeductible() {
        return deductible;
    }

    /** Stores the deductible value supplied by the caller. */
    public void setDeductible(double deductible) {
        this.deductible = deductible;
    }

    /** Returns the annualPremium value. */
    public double getAnnualPremium() {
        return annualPremium;
    }

    /** Stores the annualPremium value supplied by the caller. */
    public void setAnnualPremium(double annualPremium) {
        this.annualPremium = annualPremium;
    }

    /** Returns the status value. */
    public String getStatus() {
        return status;
    }

    /** Stores the status value supplied by the caller. */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("Policy[");
        text.append("policyId=").append(policyId);        text.append(", ");
        text.append("policyNumber=").append(policyNumber);        text.append(", ");
        text.append("lineOfBusiness=").append(lineOfBusiness);        text.append(", ");
        text.append("insuredName=").append(insuredName);        text.append(", ");
        text.append("insuredAddress=").append(insuredAddress);        text.append(", ");
        text.append("effectiveDate=").append(effectiveDate);        text.append(", ");
        text.append("expiryDate=").append(expiryDate);        text.append(", ");
        text.append("policyLimit=").append(policyLimit);        text.append(", ");
        text.append("deductible=").append(deductible);        text.append(", ");
        text.append("annualPremium=").append(annualPremium);        text.append(", ");
        text.append("status=").append(status);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
