package com.northstar.claims.web;

import com.northstar.claims.model.Claim;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the WorkbenchViewAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class WorkbenchViewAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        Claim claim = authorizedClaim(request, claimId);
        if (claim == null) {
            return denyClaimAccess(mapping, request, response, claimId);
        }
        request.setAttribute("claim", claim);
        request.setAttribute("claimId", new Integer(claim.getClaimId()));
        request.setAttribute("claimStatus", claim.getStatus());
        request.setAttribute("reserveAmount",
                new Double(claim.getReserveAmount()));
        request.setAttribute("assignedAdjuster",
                claim.getAssignedAdjuster());
        request.setAttribute("lossDate", claim.getLossDate());
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
