package com.northstar.payment.web;

/**
 * A request the monolith ends on error.jsp through its global exception
 * mapping (src/main/webapp/WEB-INF/struts-config.xml:59-60): no settlement for
 * the claim, or no payment for the id.
 */
public class PaymentFailure extends RuntimeException {
    public PaymentFailure(String message) {
        super(message);
    }
}
