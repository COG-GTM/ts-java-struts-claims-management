package com.northstar.payment.web;

/**
 * PAY-R17: an unknown paymentId makes PaymentDetailAction dereference a null
 * payment, which the global exception mapping turns into error.jsp
 * (src/main/java/com/northstar/claims/web/PaymentDetailAction.java:18-20;
 * src/main/webapp/WEB-INF/struts-config.xml:59-60).
 */
public class MissingPaymentException extends RuntimeException {
    public MissingPaymentException(int paymentId) {
        super("payment " + paymentId + " does not exist");
    }
}
