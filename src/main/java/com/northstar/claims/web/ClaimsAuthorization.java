package com.northstar.claims.web;

/**
 * Central authorization rules for the claims application. Operators are
 * scoped to the claims assigned to them; supervisors carry the ALL region
 * scope and are the only payment authorizers.
 */
public final class ClaimsAuthorization {

    public static final String ROLE_SUPERVISOR = "SUPERVISOR";
    public static final String ROLE_ADJUSTER = "ADJUSTER";

    private static final String SUPERVISOR_USER = "supervisor";

    private ClaimsAuthorization() {
    }

    /** Resolves the role granted to an operator username at login time. */
    public static String roleFor(String operator) {
        if (!hasText(operator)) {
            return ROLE_ADJUSTER;
        }
        return SUPERVISOR_USER.equalsIgnoreCase(operator.trim())
                ? ROLE_SUPERVISOR : ROLE_ADJUSTER;
    }

    /** Supervisor only when both the session and the operator grant it. */
    public static String leastPrivileged(String storedRole,
            String entitledRole) {
        return isSupervisor(storedRole) && isSupervisor(entitledRole)
                ? ROLE_SUPERVISOR : ROLE_ADJUSTER;
    }

    public static boolean isSupervisor(String role) {
        return ROLE_SUPERVISOR.equals(role);
    }

    /** True when the operator owns the claim or supervises all claims. */
    public static boolean canAccessClaim(String operator, String role,
            String assignedAdjuster) {
        if (!hasText(operator) || !hasText(role)) {
            return false;
        }
        if (isSupervisor(role)) {
            return true;
        }
        return hasText(assignedAdjuster)
                && assignedAdjuster.trim().equalsIgnoreCase(operator.trim());
    }

    /** Issuing a payment additionally requires the payment authorizer role. */
    public static boolean canIssuePayment(String operator, String role,
            String assignedAdjuster) {
        return isSupervisor(role)
                && canAccessClaim(operator, role, assignedAdjuster);
    }

    /** Portfolio-wide reporting and reference screens are supervisor only. */
    public static boolean canReadPortfolio(String operator, String role) {
        return hasText(operator) && isSupervisor(role);
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }
}
