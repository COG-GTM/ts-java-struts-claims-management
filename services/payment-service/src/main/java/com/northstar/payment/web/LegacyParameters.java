package com.northstar.payment.web;

/**
 * The request parameter coercion of the monolith's ClaimsActionSupport,
 * copied verbatim (src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-45).
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R02 — {@code integer(claimId, 119)}: a missing or unparseable
 *       claimId is claim 119 (PaymentIssueAction.java:22; PaymentHistoryAction.java:20;
 *       PaymentRemittanceAction.java:17).</li>
 *   <li>PAY-R04 — {@code decimal(amount, settlementAmount)}: a missing or
 *       unparseable amount is the latest settlement's amount
 *       (PaymentIssueAction.java:30-31).</li>
 *   <li>PAY-R05 — {@code Double.parseDouble} accepts negative values, NaN,
 *       exponent notation and 1.005 unchanged (ClaimsActionSupport.java:39-45).</li>
 *   <li>PAY-R16 — {@code integer(paymentId, 61)} (PaymentDetailAction.java:17).</li>
 * </ul>
 */
public final class LegacyParameters {
    private LegacyParameters() {
    }

    /** ClaimsActionSupport.java:31-37. */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** ClaimsActionSupport.java:39-45. */
    public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception failure) {
            return fallback;
        }
    }
}
