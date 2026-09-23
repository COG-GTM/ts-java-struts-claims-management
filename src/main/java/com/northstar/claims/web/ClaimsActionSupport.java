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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.Action;
import com.northstar.claims.model.Claim;

/**
 * Supplies only the small pieces of plumbing shared by screen actions.
 */
public abstract class ClaimsActionSupport extends Action {

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

    protected int update(String sql) throws Exception {
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

    protected Claim findClaim(int claimId) {
        try {
            return new com.northstar.claims.dao.ClaimDAO().findById(claimId);
        } catch (Exception failure) {
            log.warn("Claim lookup failed for " + claimId, failure);
            return null;
        }
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
     * Returns the region of the signed-in operator, caching it on the
     * session. An unknown operator has no region and therefore no scope.
     */
    protected String operatorRegion(
            javax.servlet.http.HttpServletRequest request) {
        javax.servlet.http.HttpSession session = request.getSession();
        Object cached = session.getAttribute("operatorRegion");
        if (cached != null) {
            return String.valueOf(cached);
        }
        String region = "";
        try {
            com.northstar.claims.model.Adjuster operator =
                    new com.northstar.claims.dao.AdjusterDAO()
                            .findByUsername(currentOperator(request));
            if (operator != null && operator.isActive()) {
                region = defaultText(operator.getRegion(), "");
            }
        } catch (Exception failure) {
            log.warn("Operator scope lookup failed for "
                    + currentOperator(request), failure);
        }
        session.setAttribute("operatorRegion", region);
        return region;
    }

    /**
     * Loads a claim only when the signed-in operator owns it or supervises
     * every queue, and returns null when access must be refused.
     */
    protected Claim authorizedClaim(
            javax.servlet.http.HttpServletRequest request, int claimId) {
        Claim claim = findClaim(claimId);
        String operator = currentOperator(request);
        if (com.northstar.claims.service.ClaimAccessPolicy.permits(operator,
                operatorRegion(request), claim)) {
            return claim;
        }
        log.warn("Operator " + operator + " refused access to claim "
                + claimId);
        return null;
    }

    /** Keeps only the claims the signed-in operator is allowed to see. */
    protected java.util.List scopedClaims(
            javax.servlet.http.HttpServletRequest request,
            java.util.List claims) {
        java.util.List visible = new java.util.ArrayList();
        if (claims == null) {
            return visible;
        }
        String operator = currentOperator(request);
        String region = operatorRegion(request);
        for (java.util.Iterator it = claims.iterator(); it.hasNext();) {
            Object row = it.next();
            if (row instanceof Claim
                    && com.northstar.claims.service.ClaimAccessPolicy.permits(
                            operator, region, (Claim) row)) {
                visible.add(row);
            }
        }
        return visible;
    }

    /** Renders the refusal screen used when a claim is out of scope. */
    protected org.apache.struts.action.ActionForward denyClaimAccess(
            org.apache.struts.action.ActionMapping mapping,
            javax.servlet.http.HttpServletRequest request,
            javax.servlet.http.HttpServletResponse response, int claimId) {
        response.setStatus(
                javax.servlet.http.HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("message", "errors.claimAccess.denied");
        request.setAttribute("accessStatus", "DENIED");
        request.setAttribute("screenName", "detail");
        return mapping.findForward("accessDenied");
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
