package com.northstar.settlement.web;

/**
 * Raised on the save path when the claim, or the claim's policy, does not
 * exist.
 *
 * <p>Implements SPEC-SETTLE-001 SETTLE-R26: the save action dereferences the
 * claim and its policy without a null check and has no 10000 fallback, so a
 * claimId with no claim row raises an exception and reaches error.jsp
 * (src/main/java/com/northstar/claims/web/SettlementSaveAction.java:25-33).
 */
public class MissingClaimException extends RuntimeException {

    public MissingClaimException(int claimId) {
        super("No claim or policy for claimId " + claimId);
    }
}
