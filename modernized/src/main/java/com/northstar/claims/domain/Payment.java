package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class Payment {

    private static final Log log = LogFactory.getLog(Payment.class);

    private int paymentId;
    private int claimId;
    private int settlementId;
    private String payeeName;
    private double amount;
    private String paymentMethod;
    private String checkNumber;
    private String issuedDate;
    private String status;

    /** Returns the paymentId value. */
    public int getPaymentId() {
        return paymentId;
    }

    /** Stores the paymentId value supplied by the caller. */
    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    /** Returns the claimId value. */
    public int getClaimId() {
        return claimId;
    }

    /** Stores the claimId value supplied by the caller. */
    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    /** Returns the settlementId value. */
    public int getSettlementId() {
        return settlementId;
    }

    /** Stores the settlementId value supplied by the caller. */
    public void setSettlementId(int settlementId) {
        this.settlementId = settlementId;
    }

    /** Returns the payeeName value. */
    public String getPayeeName() {
        return payeeName;
    }

    /** Stores the payeeName value supplied by the caller. */
    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    /** Returns the amount value. */
    public double getAmount() {
        return amount;
    }

    /** Stores the amount value supplied by the caller. */
    public void setAmount(double amount) {
        this.amount = amount;
    }

    /** Returns the paymentMethod value. */
    public String getPaymentMethod() {
        return paymentMethod;
    }

    /** Stores the paymentMethod value supplied by the caller. */
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    /** Returns the checkNumber value. */
    public String getCheckNumber() {
        return checkNumber;
    }

    /** Stores the checkNumber value supplied by the caller. */
    public void setCheckNumber(String checkNumber) {
        this.checkNumber = checkNumber;
    }

    /** Returns the issuedDate value. */
    public String getIssuedDate() {
        return issuedDate;
    }

    /** Stores the issuedDate value supplied by the caller. */
    public void setIssuedDate(String issuedDate) {
        this.issuedDate = issuedDate;
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
        text.append("Payment[");
        text.append("paymentId=").append(paymentId);        text.append(", ");
        text.append("claimId=").append(claimId);        text.append(", ");
        text.append("settlementId=").append(settlementId);        text.append(", ");
        text.append("payeeName=").append(payeeName);        text.append(", ");
        text.append("amount=").append(amount);        text.append(", ");
        text.append("paymentMethod=").append(paymentMethod);        text.append(", ");
        text.append("checkNumber=").append(checkNumber);        text.append(", ");
        text.append("issuedDate=").append(issuedDate);        text.append(", ");
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
