package com.northstar.payment.web;

/**
 * The lenient parameter coercion of
 * src/main/java/com/northstar/claims/web/ClaimsActionSupport.java, which every
 * payment action inherits, and the literal defaults the actions pass to it.
 */
public final class LegacyParameters {

    /** PAY-R02: PaymentIssueAction.java:22, PaymentHistoryAction.java:20, PaymentRemittanceAction.java:17. */
    public static final int DEFAULT_CLAIM_ID = 119;

    /** PAY-R20: PaymentDetailAction.java:17. */
    public static final int DEFAULT_PAYMENT_ID = 61;

    /** PAY-R11: PaymentIssueAction.java:34-35. */
    public static final String ISSUED_DATE = "2019-04-03";
    public static final String ISSUED_STATUS = "ISSUED";

    private LegacyParameters() {
    }

    /** PAY-R02, PAY-R20: {@code integer(value, fallback)} of ClaimsActionSupport:31-37. */
    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** PAY-R05: {@code decimal(value, fallback)} of ClaimsActionSupport:39-45. */
    public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    /** PAY-R10: the unpadded check number of PaymentIssueAction.java:33. */
    public static String checkNumber(int paymentId) {
        return "CHK-" + paymentId;
    }
}
