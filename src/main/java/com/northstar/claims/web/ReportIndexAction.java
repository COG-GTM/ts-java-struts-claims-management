package com.northstar.claims.web;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Opens the reporting index and its available report links. */
public class ReportIndexAction extends ClaimsActionSupport {
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!canReadPortfolio(request)) {
            return denied(mapping, request);
        }
        request.setAttribute("reportAsOf", reportDate());
        request.setAttribute("screenName", "report-index");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        request.setAttribute("reportCount", new Integer(7));
        request.setAttribute("reportArea", "claims operations");
        request.setAttribute("reportNavigation", "standard");
        request.setAttribute("reportAccess", "supervisor");
        request.setAttribute("reportFormat", "html");
        return mapping.findForward("reportIndex");
    }
}
