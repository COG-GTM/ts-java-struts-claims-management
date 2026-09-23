package com.northstar.claims.web;

import java.util.List;
import com.northstar.claims.dao.ReportDAO;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the LossRatioReportAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class LossRatioReportAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!canReadPortfolio(request)) {
            return denied(mapping, request);
        }
        List rows = new ReportDAO().lossRatioByLine();
        request.setAttribute("reportRows", rows);
        request.setAttribute("reportType", "lossRatio");
        request.setAttribute("reportRowCount", new Integer(rows.size()));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("report");
    }
}
