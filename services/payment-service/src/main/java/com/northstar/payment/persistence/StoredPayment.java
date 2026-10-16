package com.northstar.payment.persistence;

/**
 * One PAYMENT row as the legacy Payment model holds it
 * (src/main/resources/db/schema.sql:106-118; PaymentDAO.java). {@code amount}
 * is the raw DOUBLE: PAY-R05 stores what {@code Double.parseDouble} returned.
 * {@code issuedDate} is the DATE column read as text, yyyy-MM-dd.
 */
public record StoredPayment(int paymentId, int claimId, int settlementId, String payeeName,
        double amount, String paymentMethod, String checkNumber, String issuedDate,
        String status) {
}
