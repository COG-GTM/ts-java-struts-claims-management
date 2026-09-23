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
        String adjuster = request.getParameter("adjuster");
        if (adjuster == null || adjuster.length() == 0) {
            adjuster = "adjuster2";
        }
        update("update CLAIM set assigned_adjuster = ? where claim_id = ?",
                new Object[] { adjuster, new Integer(claimId) });
        Claim claim = findClaim(claimId);
        request.setAttribute("claim", claim);
        request.setAttribute("assignedAdjuster", adjuster);
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
