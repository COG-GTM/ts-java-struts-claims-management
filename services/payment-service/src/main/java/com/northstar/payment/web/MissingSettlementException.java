package com.northstar.payment.web;

/** PAY-R04: the claim has no SETTLEMENT row, so the legacy issue action dereferences null. */
public class MissingSettlementException extends RuntimeException {

    public MissingSettlementException(int claimId) {
        super("claim " + claimId + " has no settlement");
    }
}
