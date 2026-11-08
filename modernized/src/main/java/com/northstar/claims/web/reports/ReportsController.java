package com.northstar.claims.web.reports;

import com.northstar.claims.repository.ClaimDAO;
import com.northstar.claims.repository.PolicyDAO;
import com.northstar.claims.repository.ReportDAO;
import com.northstar.claims.support.ClaimsSupport;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Read-only report actions retain the legacy mappings and ignore request parameters. */
@Controller
@RequestMapping("/report")
public class ReportsController {
    private final ReportDAO reports;
    private final ClaimDAO claims;
    private final PolicyDAO policies;
    private final ClaimsSupport support;

    public ReportsController(ReportDAO reports, ClaimDAO claims, PolicyDAO policies, ClaimsSupport support) {
        this.reports = reports;
        this.claims = claims;
        this.policies = policies;
        this.support = support;
    }

    @RequestMapping("/openByAdjuster.do")
    public String openByAdjuster(Model model) throws SQLException {
        return summary(model, reports.openClaimsByAdjuster(), "openByAdjuster");
    }

    @RequestMapping("/lossRatio.do")
    public String lossRatio(Model model) throws SQLException {
        return summary(model, reports.lossRatioByLine(), "lossRatio");
    }

    @RequestMapping("/agedClaims.do")
    public String agedClaims(Model model) throws SQLException {
        return summary(model, reports.agedClaims(), "agedClaims");
    }

    @RequestMapping("/adjusterWorkload.do")
    public String adjusterWorkload(Model model) throws SQLException {
        List<?> rows = reports.openClaimsByAdjuster();
        model.addAttribute("reportRows", rows);
        model.addAttribute("reportRowCount", rows.size());
        return detail(model, "adjusterWorkload");
    }

    @RequestMapping("/claimAgingDetail.do")
    public String claimAgingDetail(Model model) throws SQLException {
        // Unlike the aggregate aging report, this detail deliberately includes only OPEN claims.
        List<?> rows = claims.findByStatus("OPEN");
        model.addAttribute("claims", rows);
        model.addAttribute("claimCount", rows.size());
        return detail(model, "claimAgingDetail");
    }

    @RequestMapping("/reconciliation.do")
    public String reconciliation(Model model) throws SQLException {
        List<?> rows = reports.lossRatioByLine();
        model.addAttribute("reportRows", rows);
        model.addAttribute("reportRowCount", rows.size());
        return detail(model, "reconciliation");
    }

    @RequestMapping("/premiumDetail.do")
    public String premiumDetail(Model model) throws SQLException {
        List<?> rows = policies.findAll();
        model.addAttribute("policies", rows);
        model.addAttribute("policyCount", rows.size());
        return detail(model, "premiumDetail");
    }

    @RequestMapping("/index.do")
    public String index(Model model) {
        detail(model, "index");
        model.addAttribute("screenName", "report-index");
        model.addAttribute("reportCount", 7);
        model.addAttribute("reportArea", "claims operations");
        model.addAttribute("reportNavigation", "standard");
        model.addAttribute("reportAccess", "supervisor");
        model.addAttribute("reportFormat", "html");
        return "reports/index";
    }

    private String summary(Model model, List<?> rows, String type) {
        model.addAttribute("reportRows", rows);
        model.addAttribute("reportType", type);
        model.addAttribute("reportRowCount", rows.size());
        model.addAttribute("screenName", "detail");
        return "reports/" + type;
    }

    private String detail(Model model, String view) {
        model.addAttribute("reportAsOf", support.reportDate());
        model.addAttribute("screenName", "report-detail");
        model.addAttribute("screenMode", "read");
        model.addAttribute("operatorScope", "claims");
        return "reports/" + view;
    }
}
