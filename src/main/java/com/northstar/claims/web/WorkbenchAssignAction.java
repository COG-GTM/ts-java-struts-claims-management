package com.northstar.claims.web;

import com.northstar.claims.dao.AdjusterDAO;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.model.Claim;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the WorkbenchAssignAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class WorkbenchAssignAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        String adjuster = request.getParameter("adjuster");
        if (adjuster == null || adjuster.length() == 0) {
            adjuster = "adjuster2";
        }
        if (new AdjusterDAO().findActiveByUsername(adjuster) == null) {
            log.warn("Rejected assignment of claim " + claimId
                    + " to unknown or inactive adjuster");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Unknown adjuster");
            return null;
        }
        new ClaimDAO().assign(claimId, adjuster);
        Claim claim = findClaim(claimId);
        request.setAttribute("claim", claim);
        request.setAttribute("assignedAdjuster", adjuster);
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
