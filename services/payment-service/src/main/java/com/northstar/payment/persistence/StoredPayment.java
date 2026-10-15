package com.northstar.payment.persistence;

/**
 * One PAYMENT row, the nine columns of
 * src/main/resources/db/schema.sql:106-119 as model/Payment.java:14-22 holds
 * them (PAY-R12). {@code issuedDate} is the yyyy-MM-dd string the legacy DAO
 * reads (src/main/java/com/northstar/claims/dao/PaymentDAO.java:167-179).
 */
public record StoredPayment(
        int paymentId,
        int claimId,
        int settlementId,
        String payeeName,
        double amount,
        String paymentMethod,
        String checkNumber,
        String issuedDate,
        String status) {
}
