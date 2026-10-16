package com.northstar.payment.persistence;

/** One PAYMENT row (src/main/resources/db/schema.sql:106-118). */
public record StoredPayment(int paymentId, int claimId, int settlementId,
        String payeeName, double amount, String paymentMethod,
        String checkNumber, String issuedDate, String status) {
}
