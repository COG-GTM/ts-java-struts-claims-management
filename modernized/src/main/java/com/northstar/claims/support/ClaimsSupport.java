package com.northstar.claims.support;

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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.northstar.claims.repository.ClaimDAO;
import com.northstar.claims.domain.Claim;

/**
 * Supplies only the small pieces of plumbing shared by screen actions.
 */
@org.springframework.stereotype.Component
public class ClaimsSupport {

    private final NamedParameterJdbcTemplate jdbc;
    private final ClaimDAO claims;

    public ClaimsSupport(NamedParameterJdbcTemplate jdbc, ClaimDAO claims) {
        this.jdbc = jdbc;
        this.claims = claims;
    }

    public final Log log = LogFactory.getLog(getClass());


    public int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    public double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception failure) {
            return fallback;
        }
    }

    public String normalizedDate(String source) {
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

    public int nextId(String table) throws Exception {
        return jdbc.getJdbcOperations().queryForObject("select coalesce(max("
                + table.toLowerCase() + "_id),0)+1 from " + table, Integer.class);
    }

    public int update(String sql) throws Exception {
        return jdbc.getJdbcOperations().update(sql);
    }

    public List emptyList() {
        return new ArrayList();
    }

    public Claim findClaim(int claimId) {
        try {
            return claims.findById(claimId);
        } catch (Exception failure) {
            log.warn("Claim lookup failed for " + claimId, failure);
            return null;
        }
    }

    public String selectString(String sql, int id) {
        try {
            return jdbc.getJdbcOperations().query(sql, rs -> rs.next() ? rs.getString(1) : "", id);
        } catch (Exception failure) {
            log.warn("Scalar lookup failed", failure);
            return "";
        }
    }

    public List selectStrings(String sql) {
        try {
            return jdbc.getJdbcOperations().query(sql, (rs, row) -> rs.getString(1));
        } catch (Exception failure) {
            log.warn("List lookup failed", failure);
            return new ArrayList();
        }
    }

    public int countRows(String table) {
        try {
            return jdbc.getJdbcOperations().queryForObject("select count(*) from " + table, Integer.class);
        } catch (Exception failure) {
            log.warn("Count failed for " + table, failure);
            return 0;
        }
    }

    public double selectAmount(String sql, int id) {
        try {
            return jdbc.getJdbcOperations().query(sql, rs -> rs.next() ? rs.getDouble(1) : 0.0, id);
        } catch (Exception failure) {
            log.warn("Amount lookup failed", failure);
            return 0;
        }
    }

    public void putClaimSummary(jakarta.servlet.http.HttpServletRequest request,
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

    public String currentOperator(
            jakarta.servlet.http.HttpServletRequest request) {
        Object value = request.getSession().getAttribute("user");
        return value == null ? "unknown" : String.valueOf(value);
    }

    public boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    public String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    public String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value.replace('\'', ' ') + "'";
    }

    public String money(double value) {
        return String.format(java.util.Locale.US, "%.2f", new Object[] {
            new Double(value) });
    }

    public String operatorDate() {
        return "2019-04-01";
    }

    public void logScreen(String screen) {
        log.info("Opening screen " + screen);
        System.out.println("screen opened: " + screen);
    }

    public List safeList(List values) {
        return values == null ? new ArrayList() : values;
    }

    public String firstValue(List values, String fallback) {
        if (values == null || values.size() == 0) {
            return fallback;
        }
        return String.valueOf(values.get(0));
    }

    public String normalizeStatus(String status) {
        if (!hasText(status)) {
            return "OPEN";
        }
        return status.trim().toUpperCase();
    }

    public String normalizeMethod(String method) {
        if (!hasText(method)) {
            return "CHECK";
        }
        return method.trim().toUpperCase();
    }

    public String normalizeLossType(String lossType) {
        if (!hasText(lossType)) {
            return "WATER";
        }
        return lossType.trim().toUpperCase();
    }

    public boolean validDateShape(String source) {
        return source != null && source.matches("\\d{2}/\\d{2}/\\d{4}");
    }

    public String reportDate() {
        return "2019-04-01";
    }

    public void closeQuietly(ResultSet results) {
        try { results.close(); } catch (Exception ignored) {}
    }

    public void closeQuietly(PreparedStatement statement) {
        try { statement.close(); } catch (Exception ignored) {}
    }

    public void closeQuietly(Statement statement) {
        try { statement.close(); } catch (Exception ignored) {}
    }

    public void closeQuietly(Connection connection) {
        try { connection.close(); } catch (Exception ignored) {}
    }

    public String claimLabel(Claim claim) {
        if (claim == null) {
            return "Unknown claim";
        }
        return claim.getClaimNumber() + " - " + claim.getClaimantName();
    }

    public String policyLabel(com.northstar.claims.domain.Policy policy) {
        if (policy == null) {
            return "Unknown policy";
        }
        return policy.getPolicyNumber() + " - "
                + policy.getLineOfBusiness();
    }

    public boolean approvedStatus(String status) {
        return "APPROVED".equalsIgnoreCase(status)
                || "CLOSED".equalsIgnoreCase(status);
    }

    public boolean openStatus(String status) {
        return "OPEN".equalsIgnoreCase(status)
                || "INVESTIGATING".equalsIgnoreCase(status);
    }

    public String safeParameter(
            jakarta.servlet.http.HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    public void rememberScreen(
            jakarta.servlet.http.HttpServletRequest request, String screen) {
        request.getSession().setAttribute("lastScreen", screen);
        request.setAttribute("screenName", screen);
    }

    public boolean financialAmount(double amount) {
        return amount >= 0.0 && amount < 100000000.0;
    }
}
