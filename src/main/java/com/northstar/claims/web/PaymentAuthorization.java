package com.northstar.claims.web;

import com.northstar.claims.model.Claim;

/**
 * Decides whether an operator may issue a payment against a claim.
 * Supervisors may issue on any claim; an adjuster may only issue on the
 * claims assigned to them.
 */
public final class PaymentAuthorization {

    public static final String ROLE_SUPERVISOR = "SUPERVISOR";
    public static final String ROLE_ADJUSTER = "ADJUSTER";
    public static final String ROLE_NONE = "NONE";

    private PaymentAuthorization() {
    }

    /** Maps a login name onto the operator role it carries. */
    public static String roleFor(String username) {
        if (username == null) {
            return ROLE_NONE;
        }
        String name = username.trim().toLowerCase();
        if ("supervisor".equals(name)) {
            return ROLE_SUPERVISOR;
        }
        if (name.startsWith("adjuster")) {
            return ROLE_ADJUSTER;
        }
        return ROLE_NONE;
    }

    /** Answers whether the operator may cut a check for the given claim. */
    public static boolean canIssuePayment(String operator, String role,
            Claim claim) {
        if (claim == null || operator == null || operator.trim().length() == 0) {
            return false;
        }
        if (ROLE_SUPERVISOR.equals(role)) {
            return true;
        }
        if (!ROLE_ADJUSTER.equals(role)) {
            return false;
        }
        String assigned = claim.getAssignedAdjuster();
        return assigned != null && assigned.trim().equalsIgnoreCase(operator.trim());
    }
}
