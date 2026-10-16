package com.northstar.payment.web;

/**
 * PAY-R23: no PAYMENT row has the id, so
 * src/main/webapp/WEB-INF/jsp/payment/detail.jsp:10 fails on the null payment.
 */
public class MissingPaymentException extends RuntimeException {

    public MissingPaymentException(int paymentId) {
        super("payment " + paymentId + " does not exist");
    }
}
