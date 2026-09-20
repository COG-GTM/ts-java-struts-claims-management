package com.northstar.settlement.domain;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(int claimId) {
        super("Claim " + claimId + " has no policy");
    }
}
