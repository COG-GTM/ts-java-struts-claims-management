package com.northstar.payment.web;

/**
 * PAY-R11: a claim with no SETTLEMENT row makes PaymentIssueAction dereference
 * a null settlement, which the monolith's global exception mapping turns into
 * error.jsp (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,28;
 * src/main/webapp/WEB-INF/struts-config.xml:59-60). No PAYMENT row is written.
 */
public class MissingSettlementException extends RuntimeException {
    public MissingSettlementException(int claimId) {
        super("claim " + claimId + " has no settlement");
    }
}
