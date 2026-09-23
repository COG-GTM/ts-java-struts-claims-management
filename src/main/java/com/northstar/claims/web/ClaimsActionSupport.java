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

    protected int update(String sql, Object[] parameters) throws Exception {
        Connection connection = openConnection();
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql);
            for (int i = 0; parameters != null && i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return statement.executeUpdate();
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
