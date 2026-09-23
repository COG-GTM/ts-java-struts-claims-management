package com.northstar.claims.web;

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
        if (!canReadPortfolio(request)
                || authorizedClaim(request, claimId) == null) {
            return denied(mapping, request);
        }
        String adjuster = defaultText(request.getParameter("adjuster"),
                "adjuster2");
        updateClaim(request, claimId, "update CLAIM set assigned_adjuster = "
                + quote(adjuster) + " where claim_id = " + claimId);
        Claim claim = authorizedClaim(request, claimId);
        request.setAttribute("claim", claim);
        request.setAttribute("assignedAdjuster", adjuster);
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
