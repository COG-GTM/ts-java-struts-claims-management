package com.northstar.claims.web;

import com.northstar.claims.dao.ReportDAO;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the reconciliation screen data and selects its configured forward. */
public class ReconciliationAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!canReadPortfolio(request)) {
            return denied(mapping, request);
        }
        java.util.List rows = new ReportDAO().lossRatioByLine();
        request.setAttribute("reportRows", rows);
        request.setAttribute("reportRowCount", new Integer(rows.size()));
        request.setAttribute("reportAsOf", reportDate());
        request.setAttribute("screenName", "report-detail");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        return mapping.findForward("reconciliation");
    }
}
