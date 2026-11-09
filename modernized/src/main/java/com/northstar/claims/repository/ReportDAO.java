package com.northstar.claims.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Produces the aggregate rows displayed by the claims reporting screens.
 * Each query returns raw maps because that was the convention used by the
 * report framework shared by the application in this period.
 */
@org.springframework.stereotype.Repository
public class ReportDAO {

    private final org.springframework.jdbc.core.JdbcOperations jdbc;

    public ReportDAO(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        this.jdbc = template.getJdbcOperations();
    }

    @Value("${report.asof.date:2019-04-01}")
    private String asOfDate;

    private static final Log log = LogFactory.getLog(ReportDAO.class);

    /** Groups open claims by assigned adjuster. */
    public List openClaimsByAdjuster() throws SQLException {
        List rows = new ArrayList();
        return jdbc.query(
                    "select assigned_adjuster, count(*) as open_count, "
                    + "coalesce(sum(reserve_amount),0) as reserve_total "
                    + "from CLAIM where status in ('OPEN','INVESTIGATING') "
                    + "group by assigned_adjuster order by assigned_adjuster", rs -> {
            while (rs.next()) {
                Map row = new HashMap();
                row.put("adjuster", rs.getString("assigned_adjuster"));
                row.put("openCount", new Double(rs.getDouble("open_count")));
                row.put("reserveTotal", new Double(rs.getDouble("reserve_total")));
                rows.add(row);
            }
            return rows;
        });
    }

    /** Calculates paid plus reserve against premium for each business line. */
    public List lossRatioByLine() throws SQLException {
        List rows = new ArrayList();
            StringBuffer sql = new StringBuffer();
            sql.append("select p.line_of_business, sum(p.annual_premium) premium_total,");
            sql.append(" coalesce(sum(c.reserve_amount),0) reserve_total,");
            sql.append(" coalesce(sum(pay.amount),0) paid_total");
            sql.append(" from POLICY p left join CLAIM c on p.policy_id = c.policy_id");
            sql.append(" left join PAYMENT pay on c.claim_id = pay.claim_id");
            sql.append(" group by p.line_of_business order by p.line_of_business");
        return jdbc.query(sql.toString(), rs -> {
            while (rs.next()) {
                Map row = new HashMap();
                double premium = rs.getDouble("premium_total");
                double loss = rs.getDouble("reserve_total") + rs.getDouble("paid_total");
                row.put("lineOfBusiness", rs.getString("line_of_business"));
                row.put("premiumTotal", new Double(premium));
                row.put("lossTotal", new Double(loss));
                row.put("lossRatio", new Double(premium == 0 ? 0 : loss / premium));
                rows.add(row);
            }
            return rows;
        });
    }

    /** Places claims into age buckets based on their reported date. */
    public List agedClaims() throws SQLException {
        List rows = new ArrayList();
        return jdbc.query(
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
                    + "then '61_90' else '91_PLUS' end order by age_bucket", rs -> {
            while (rs.next()) {
                Map row = new HashMap();
                row.put("bucket", rs.getString("age_bucket").trim());
                row.put("claimCount", new Double(rs.getDouble("claim_count")));
                row.put("reserveTotal", new Double(rs.getDouble("reserve_total")));
                rows.add(row);
            }
            return rows;
        });
    }

    /** Keeps the report ordering query in the form used by old ad-hoc reports. */
    public List claimCounts(String orderBy) throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select status, count(*) as count from CLAIM group by status order by " + orderBy, rs -> {
            while (rs.next()) {
                Map row = new HashMap();
                row.put("status", rs.getString("status"));
                row.put("count", new Double(rs.getDouble("count")));
                rows.add(row);
            }
            return rows;
        });
    }

    /** Records a report query in the application log. */
    private void trace(String text) {
        log.info(text);
        System.out.println(text);
    }
}
