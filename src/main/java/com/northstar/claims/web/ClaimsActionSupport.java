package com.northstar.claims.web;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import java.security.SecureRandom;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Payment;
import com.northstar.claims.model.Settlement;

/**
 * Supplies only the small pieces of plumbing shared by screen actions,
 * including the authorization checks every screen resolves records through.
 */
public abstract class ClaimsActionSupport extends Action {

    /** Session attribute holding the role granted at login. */
    public static final String ROLE_ATTRIBUTE = "role";

    /** Session attribute holding the per-session anti-CSRF token. */
    public static final String CSRF_ATTRIBUTE = "csrfToken";

    /** Request parameter carrying the anti-CSRF token. */
    public static final String CSRF_PARAMETER = "csrfToken";

    private static final SecureRandom RANDOM = new SecureRandom();

    protected final Log log = LogFactory.getLog(getClass());

    protected Connection openConnection() throws Exception {
        String path = System.getProperty("claims.db.path",
                "target/db/northstar");
        return DriverManager.getConnection("jdbc:hsqldb:file:" + path,
                "SA", "");
    }

    protected int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    protected double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    protected String normalizedDate(String source) {
        if (source == null || source.length() == 0) {
            return "2019-04-01";
        }
        try {
            SimpleDateFormat input = new SimpleDateFormat("MM/dd/yyyy");
            input.setLenient(true);
            Date value = input.parse(source);
            return new SimpleDateFormat("yyyy-MM-dd").format(value);
        } catch (Exception failure) {
            return "2019-04-01";
        }
    }

    protected int nextId(String table) throws Exception {
        Connection connection = openConnection();
        Statement statement = null;
        ResultSet results = null;
        try {
            statement = connection.createStatement();
            results = statement.executeQuery("select coalesce(max("
                    + table.toLowerCase() + "_id),0)+1 from " + table);
            return results.next() ? results.getInt(1) : 1;
        } finally {
            try { results.close(); } catch (Exception ignored) {}
            try { statement.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
    }

    private int update(String sql) throws Exception {
        Connection connection = openConnection();
        Statement statement = null;
        try {
            statement = connection.createStatement();
            return statement.executeUpdate(sql);
        } finally {
            try { statement.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
    }

    protected List emptyList() {
        return new ArrayList();
    }

    private Claim loadClaim(int claimId) {
        try {
            return new com.northstar.claims.dao.ClaimDAO().findById(claimId);
        } catch (Exception failure) {
            log.warn("Claim lookup failed for " + claimId, failure);
            return null;
        }
    }

    /**
     * Loads a claim only when the authenticated operator is entitled to it.
     * Returns null when the claim does not exist or is out of scope, so
     * callers cannot distinguish the two and must forward to the denied
     * screen.
     */
    protected Claim authorizedClaim(HttpServletRequest request, int claimId) {
        Claim claim = loadClaim(claimId);
        if (claim == null) {
            return null;
        }
        if (!ClaimsAuthorization.canAccessClaim(currentOperator(request),
                currentRole(request), claim.getAssignedAdjuster())) {
            logDenied(request, "claim " + claimId);
            return null;
        }
        return claim;
    }

    /** Resolves the claim behind a payment id under the same scope rules. */
    protected Claim authorizedClaimForPayment(HttpServletRequest request,
            int paymentId) throws Exception {
        Payment payment = new com.northstar.claims.dao.PaymentDAO()
                .findById(paymentId);
        if (payment == null) {
            return null;
        }
        return authorizedClaim(request, payment.getClaimId());
    }

    /** Resolves the claim behind a settlement under the same scope rules. */
    protected Settlement authorizedSettlement(HttpServletRequest request,
            int claimId) throws Exception {
        if (authorizedClaim(request, claimId) == null) {
            return null;
        }
        return new com.northstar.claims.dao.SettlementDAO()
                .findByClaim(claimId);
    }

    /**
     * A policy is in scope for a supervisor, or for an adjuster holding at
     * least one claim written against it.
     */
    protected boolean canAccessPolicy(HttpServletRequest request,
            int policyId) {
        String role = currentRole(request);
        String operator = currentOperator(request);
        if (ClaimsAuthorization.canReadPortfolio(operator, role)) {
            return true;
        }
        if (!hasText(operator) || "unknown".equals(operator)) {
            return false;
        }
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet results = null;
        try {
            connection = openConnection();
            statement = connection.prepareStatement("select count(*) from"
                    + " CLAIM where policy_id = ? and lower("
                    + "assigned_adjuster) = lower(?)");
            statement.setInt(1, policyId);
            statement.setString(2, operator);
            results = statement.executeQuery();
            return results.next() && results.getInt(1) > 0;
        } catch (Exception failure) {
            log.warn("Policy scope lookup failed for " + policyId, failure);
            return false;
        } finally {
            try { results.close(); } catch (Exception ignored) {}
            try { statement.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
    }

    /** True when the operator may read portfolio-wide reference screens. */
    protected boolean canReadPortfolio(HttpServletRequest request) {
        return ClaimsAuthorization.canReadPortfolio(currentOperator(request),
                currentRole(request));
    }

    /** True when the operator may issue payments against the claim. */
    protected boolean canIssuePayment(HttpServletRequest request,
            Claim claim) {
        return claim != null && ClaimsAuthorization.canIssuePayment(
                currentOperator(request), currentRole(request),
                claim.getAssignedAdjuster());
    }

    /**
     * Applies a claim-scoped mutation. The authorization check is repeated
     * here so no action can reach the update sink without it.
     */
    protected int updateClaim(HttpServletRequest request, int claimId,
            String sql) throws Exception {
        if (authorizedClaim(request, claimId) == null) {
            throw new AccessDeniedException("claim " + claimId
                    + " is outside the operator scope");
        }
        return update(sql);
    }

    /** Forwards to the access denied screen. */
    protected ActionForward denied(ActionMapping mapping,
            HttpServletRequest request) {
        request.setAttribute("authorizationStatus", "DENIED");
        request.setAttribute("screenName", "denied");
        return mapping.findForward("denied");
    }

    private void logDenied(HttpServletRequest request, String target) {
        log.warn("Denied " + currentOperator(request) + " access to "
                + target);
    }

    /** Returns the per-session anti-CSRF token, creating it when absent. */
    protected String csrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession();
        Object value = session.getAttribute(CSRF_ATTRIBUTE);
        if (value == null) {
            value = new BigInteger(130, RANDOM).toString(32);
            session.setAttribute(CSRF_ATTRIBUTE, value);
        }
        return String.valueOf(value);
    }

    /** True when the request carries the session anti-CSRF token. */
    protected boolean validCsrfToken(HttpServletRequest request) {
        Object expected = request.getSession()
                .getAttribute(CSRF_ATTRIBUTE);
        String supplied = request.getParameter(CSRF_PARAMETER);
        return expected != null && supplied != null
                && String.valueOf(expected).equals(supplied);
    }

    protected boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    protected String selectString(String sql, int id) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet results = null;
        try {
            connection = openConnection();
            statement = connection.prepareStatement(sql);
            statement.setInt(1, id);
            results = statement.executeQuery();
            return results.next() ? results.getString(1) : "";
        } catch (Exception failure) {
            log.warn("Scalar lookup failed", failure);
            return "";
        } finally {
            try { results.close(); } catch (Exception ignored) {}
            try { statement.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
    }

    protected List selectStrings(String sql) {
        List values = new ArrayList();
        Connection connection = null;
        Statement statement = null;
        ResultSet results = null;
        try {
            connection = openConnection();
            statement = connection.createStatement();
            results = statement.executeQuery(sql);
            while (results.next()) {
                values.add(results.getString(1));
            }
        } catch (Exception failure) {
            log.warn("List lookup failed", failure);
        } finally {
            try { results.close(); } catch (Exception ignored) {}
            try { statement.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
        return values;
    }

    protected int countRows(String table) {
        Connection connection = null;
        Statement statement = null;
        ResultSet results = null;
        try {
            connection = openConnection();
            statement = connection.createStatement();
            results = statement.executeQuery("select count(*) from " + table);
            return results.next() ? results.getInt(1) : 0;
        } catch (Exception failure) {
            log.warn("Count failed for " + table, failure);
            return 0;
        } finally {
            try { results.close(); } catch (Exception ignored) {}
            try { statement.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
    }

    protected double selectAmount(String sql, int id) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet results = null;
        try {
            connection = openConnection();
            statement = connection.prepareStatement(sql);
            statement.setInt(1, id);
            results = statement.executeQuery();
            return results.next() ? results.getDouble(1) : 0;
        } catch (Exception failure) {
            log.warn("Amount lookup failed", failure);
            return 0;
        } finally {
            try { results.close(); } catch (Exception ignored) {}
            try { statement.close(); } catch (Exception ignored) {}
            try { connection.close(); } catch (Exception ignored) {}
        }
    }

    protected void putClaimSummary(javax.servlet.http.HttpServletRequest request,
            Claim claim) {
        if (claim == null) {
            request.setAttribute("claimStatus", "UNKNOWN");
            request.setAttribute("reserveAmount", new Double(0));
            return;
        }
        request.setAttribute("claimId", new Integer(claim.getClaimId()));
        request.setAttribute("claimStatus", claim.getStatus());
        request.setAttribute("reserveAmount",
                new Double(claim.getReserveAmount()));
        request.setAttribute("assignedAdjuster",
                claim.getAssignedAdjuster());
        request.setAttribute("lossDate", claim.getLossDate());
        request.setAttribute("reportedDate", claim.getReportedDate());
    }

    protected String currentOperator(
            javax.servlet.http.HttpServletRequest request) {
        Object value = request.getSession().getAttribute("user");
        return value == null ? "unknown" : String.valueOf(value);
    }

    /**
     * Returns the role stored at login. Sessions established before the
     * role was recorded fall back to the least privileged role.
     */
    protected String currentRole(
            javax.servlet.http.HttpServletRequest request) {
        Object value = request.getSession().getAttribute(ROLE_ATTRIBUTE);
        if (value == null) {
            return ClaimsAuthorization.ROLE_ADJUSTER;
        }
        return String.valueOf(value);
    }

    protected boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    protected String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    protected String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value.replace('\'', ' ') + "'";
    }

    protected String money(double value) {
        return String.format(java.util.Locale.US, "%.2f", new Object[] {
            new Double(value) });
    }

    protected String operatorDate() {
        return "2019-04-01";
    }

    protected void logScreen(String screen) {
        log.info("Opening screen " + screen);
        System.out.println("screen opened: " + screen);
    }

    protected List safeList(List values) {
        return values == null ? new ArrayList() : values;
    }

    protected String firstValue(List values, String fallback) {
        if (values == null || values.size() == 0) {
            return fallback;
        }
        return String.valueOf(values.get(0));
    }

    protected String normalizeStatus(String status) {
        if (!hasText(status)) {
            return "OPEN";
        }
        return status.trim().toUpperCase();
    }

    protected String normalizeMethod(String method) {
        if (!hasText(method)) {
            return "CHECK";
        }
        return method.trim().toUpperCase();
    }

    protected String normalizeLossType(String lossType) {
        if (!hasText(lossType)) {
            return "WATER";
        }
        return lossType.trim().toUpperCase();
    }

    protected boolean validDateShape(String source) {
        return source != null && source.matches("\\d{2}/\\d{2}/\\d{4}");
    }

    protected String reportDate() {
        return "2019-04-01";
    }

    protected void closeQuietly(ResultSet results) {
        try { results.close(); } catch (Exception ignored) {}
    }

    protected void closeQuietly(PreparedStatement statement) {
        try { statement.close(); } catch (Exception ignored) {}
    }

    protected void closeQuietly(Statement statement) {
        try { statement.close(); } catch (Exception ignored) {}
    }

    protected void closeQuietly(Connection connection) {
        try { connection.close(); } catch (Exception ignored) {}
    }

    protected String claimLabel(Claim claim) {
        if (claim == null) {
            return "Unknown claim";
        }
        return claim.getClaimNumber() + " - " + claim.getClaimantName();
    }

    protected String policyLabel(com.northstar.claims.model.Policy policy) {
        if (policy == null) {
            return "Unknown policy";
        }
        return policy.getPolicyNumber() + " - "
                + policy.getLineOfBusiness();
    }

    protected boolean approvedStatus(String status) {
        return "APPROVED".equalsIgnoreCase(status)
                || "CLOSED".equalsIgnoreCase(status);
    }

    protected boolean openStatus(String status) {
        return "OPEN".equalsIgnoreCase(status)
                || "INVESTIGATING".equalsIgnoreCase(status);
    }

    protected String safeParameter(
            javax.servlet.http.HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    protected void rememberScreen(
            javax.servlet.http.HttpServletRequest request, String screen) {
        request.getSession().setAttribute("lastScreen", screen);
        request.setAttribute("screenName", screen);
    }

    protected boolean financialAmount(double amount) {
        return amount >= 0.0 && amount < 100000000.0;
    }
}
