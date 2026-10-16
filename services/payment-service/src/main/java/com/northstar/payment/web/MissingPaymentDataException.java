package com.northstar.payment.web;

/**
 * Raised where the legacy action dereferences a null: a claim with no
 * settlement on issue (PAY-R11, PaymentIssueAction.java:23,28) and an unknown
 * payment id on detail (PAY-R17, detail.jsp bean:write of a missing bean).
 */
public class MissingPaymentDataException extends RuntimeException {

    public MissingPaymentDataException(String message) {
        super(message);
    }
}
