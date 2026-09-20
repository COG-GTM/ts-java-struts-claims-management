package com.northstar.settlement.domain;

/**
 * Raised where the legacy save action dereferenced a null claim or policy and
 * failed with a {@code NullPointerException} (SETTLE-R15).
 */
public class ClaimNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ClaimNotFoundException(int claimId) {
        super("Claim " + claimId + " or its policy was not found");
    }
}
