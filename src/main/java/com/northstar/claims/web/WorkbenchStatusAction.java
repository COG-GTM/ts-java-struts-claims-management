package com.northstar.claims.web;

import com.northstar.claims.model.Claim;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the WorkbenchStatusAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class WorkbenchStatusAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        if (authorizedClaim(request, claimId) == null) {
            return denied(mapping, request);
        }
        String status = normalizeStatus(request.getParameter("status"));
        updateClaim(request, claimId, "update CLAIM set status = "
                + quote(status) + " where claim_id = " + claimId);
        request.setAttribute("claim", authorizedClaim(request, claimId));
        request.setAttribute("claimStatus", status);
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
