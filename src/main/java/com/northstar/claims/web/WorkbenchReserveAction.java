package com.northstar.claims.web;

import com.northstar.claims.model.Claim;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the WorkbenchReserveAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class WorkbenchReserveAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        if (authorizedClaim(request, claimId) == null) {
            return denyClaimAccess(mapping, request, response, claimId);
        }
        double reserve = decimal(request.getParameter("reserveAmount"), 4500);
        update("update CLAIM set reserve_amount = " + reserve
                + " where claim_id = " + claimId);
        request.setAttribute("claim", findClaim(claimId));
        request.setAttribute("reserveAmount", new Double(reserve));
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
