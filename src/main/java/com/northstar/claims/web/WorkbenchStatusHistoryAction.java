package com.northstar.claims.web;

import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.model.Claim;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the statusHistory screen data and selects its configured forward. */
public class WorkbenchStatusHistoryAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int id = integer(request.getParameter("claimId"), 119);
        Claim claim = authorizedClaim(request, id);
        if (claim == null) {
            return denied(mapping, request);
        }
        putClaimSummary(request, claim);
        request.setAttribute("statusHistory", new java.util.ArrayList());
        request.setAttribute("screenName", "detail");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        return mapping.findForward("statusHistory");
    }
}
