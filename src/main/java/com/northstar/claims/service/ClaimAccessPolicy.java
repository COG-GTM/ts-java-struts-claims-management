package com.northstar.claims.service;

import com.northstar.claims.model.Claim;

/**
 * Decides whether a signed-in operator may read or change one claim record.
 *
 * Claim ownership is recorded in the assigned_adjuster column. Operators
 * whose adjuster profile carries the supervisory region work every queue,
 * every other operator only works the claims assigned to them.
 */
public final class ClaimAccessPolicy {

    /** Region recorded on adjuster profiles that supervise every queue. */
    public static final String SUPERVISOR_REGION = "ALL";

    private ClaimAccessPolicy() {
    }

    /** Reports whether the region grants access to every claim queue. */
    public static boolean supervisor(String region) {
        return SUPERVISOR_REGION.equalsIgnoreCase(trimmed(region));
    }

    /** Reports whether the operator may act on the supplied claim. */
    public static boolean permits(String operator, String region,
            Claim claim) {
        if (claim == null) {
            return false;
        }
        String identity = trimmed(operator);
        if (identity.length() == 0 || "unknown".equalsIgnoreCase(identity)) {
            return false;
        }
        if (supervisor(region)) {
            return true;
        }
        return identity.equalsIgnoreCase(trimmed(claim.getAssignedAdjuster()));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
