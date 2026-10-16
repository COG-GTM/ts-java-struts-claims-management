package com.northstar.payment.web;

/**
 * PAY-R05: the claim has no SETTLEMENT row, so
 * src/main/java/com/northstar/claims/web/PaymentIssueAction.java:28
 * dereferences null before anything is written (PAY-R13).
 */
public class MissingSettlementException extends RuntimeException {

    public MissingSettlementException(int claimId) {
        super("claim " + claimId + " has no settlement");
    }
}
