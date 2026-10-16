package com.northstar.payment.persistence;

/** The two SETTLEMENT columns the issue flow reads. */
public record LatestSettlement(int settlementId, double settlementAmount) {
}
