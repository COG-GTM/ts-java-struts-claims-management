package com.northstar.claims.web;

import com.northstar.claims.dao.ClaimDAO;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the claimAgingDetail screen data and selects its configured forward. */
public class ClaimAgingDetailAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        java.util.List rows = scopedClaims(request,
                new ClaimDAO().findByStatus("OPEN"));
        request.setAttribute("claims", rows);
        request.setAttribute("claimCount", new Integer(rows.size()));
        request.setAttribute("reportAsOf", reportDate());
        request.setAttribute("screenName", "report-detail");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        return mapping.findForward("claimAgingDetail");
    }
}
