package com.northstar.claims.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.northstar.claims.util.ClaimsConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Produces the aggregate rows displayed by the claims reporting screens.
 * Each query returns raw maps because that was the convention used by the
 * report framework shared by the application in this period.
 */
public class ReportDAO {

    private static final Log log = LogFactory.getLog(ReportDAO.class);

    /** Groups open claims by assigned adjuster. */
    public List openClaimsByAdjuster() throws SQLException {
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            st = conn.createStatement();
            rs = st.executeQuery(
                    "select assigned_adjuster, count(*) as open_count, "
                    + "coalesce(sum(reserve_amount),0) as reserve_total "
                    + "from CLAIM where status in ('OPEN','INVESTIGATING') "
                    + "group by assigned_adjuster order by assigned_adjuster");
            while (rs.next()) {
                Map row = new HashMap();
                row.put("adjuster", rs.getString("assigned_adjuster"));
                row.put("openCount", Double.valueOf(rs.getDouble("open_count")));
                row.put("reserveTotal", Double.valueOf(rs.getDouble("reserve_total")));
                rows.add(row);
            }
            return rows;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { st.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Calculates paid plus reserve against premium for each business line. */
    public List lossRatioByLine() throws SQLException {
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            st = conn.createStatement();
            StringBuffer sql = new StringBuffer();
            sql.append("select p.line_of_business, sum(p.annual_premium) premium_total,");
            sql.append(" coalesce(sum(c.reserve_amount),0) reserve_total,");
            sql.append(" coalesce(sum(pay.amount),0) paid_total");
            sql.append(" from POLICY p left join CLAIM c on p.policy_id = c.policy_id");
            sql.append(" left join PAYMENT pay on c.claim_id = pay.claim_id");
            sql.append(" group by p.line_of_business order by p.line_of_business");
            rs = st.executeQuery(sql.toString());
            while (rs.next()) {
                Map row = new HashMap();
                double premium = rs.getDouble("premium_total");
                double loss = rs.getDouble("reserve_total") + rs.getDouble("paid_total");
                row.put("lineOfBusiness", rs.getString("line_of_business"));
                row.put("premiumTotal", Double.valueOf(premium));
                row.put("lossTotal", Double.valueOf(loss));
                row.put("lossRatio", Double.valueOf(premium == 0 ? 0 : loss / premium));
                rows.add(row);
            }
            return rows;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { st.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Places claims into age buckets based on their reported date. */
    public List agedClaims() throws SQLException {
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            st = conn.createStatement();
            String asOfDate = ClaimsConfig.get("report.asof.date");
            rs = st.executeQuery(
                    "select case "
                    + "when datediff('day', reported_date, date '" + asOfDate + "') <= 30 "
                    + "then '0_30' "
                    + "when datediff('day', reported_date, date '" + asOfDate + "') <= 60 "
                    + "then '31_60' "
                    + "when datediff('day', reported_date, date '" + asOfDate + "') <= 90 "
                    + "then '61_90' else '91_PLUS' end as age_bucket, "
                    + "count(*) as claim_count, "
                    + "coalesce(sum(reserve_amount),0) as reserve_total "
                    + "from CLAIM where status not in ('CLOSED','DENIED') "
                    + "group by case "
                    + "when datediff('day', reported_date, date '" + asOfDate + "') <= 30 "
                    + "then '0_30' "
                    + "when datediff('day', reported_date, date '" + asOfDate + "') <= 60 "
                    + "then '31_60' "
                    + "when datediff('day', reported_date, date '" + asOfDate + "') <= 90 "
                    + "then '61_90' else '91_PLUS' end order by age_bucket");
            while (rs.next()) {
                Map row = new HashMap();
                row.put("bucket", rs.getString("age_bucket").trim());
                row.put("claimCount", Double.valueOf(rs.getDouble("claim_count")));
                row.put("reserveTotal", Double.valueOf(rs.getDouble("reserve_total")));
                rows.add(row);
            }
            return rows;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { st.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Keeps the report ordering query in the form used by old ad-hoc reports. */
    public List claimCounts(String orderBy) throws SQLException {
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        List rows = new ArrayList();
        try {
            conn = ConnectionPool.getInstance().getConnection();
            st = conn.createStatement();
            rs = st.executeQuery("select status, count(*) as count from CLAIM group by status order by " + orderBy);
            while (rs.next()) {
                Map row = new HashMap();
                row.put("status", rs.getString("status"));
                row.put("count", Double.valueOf(rs.getDouble("count")));
                rows.add(row);
            }
            return rows;
        } finally {
            try { rs.close(); } catch (Exception e) {}
            try { st.close(); } catch (Exception e) {}
            try { ConnectionPool.getInstance().release(conn); } catch (Exception e) {}
        }
    }

    /** Records a report query in the application log. */
    private void trace(String text) {
        log.info(text);
        System.out.println(text);
    }
}
