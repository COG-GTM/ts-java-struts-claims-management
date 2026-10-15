package com.northstar.payment.web;

/** PAY-R22: no PAYMENT row has the id, so the legacy detail JSP fails on the missing bean. */
public class MissingPaymentException extends RuntimeException {

    public MissingPaymentException(int paymentId) {
        super("payment " + paymentId + " does not exist");
    }
}
