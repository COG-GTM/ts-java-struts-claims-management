package com.northstar.claims.web;

import com.northstar.claims.dao.ClaimDAO;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    private static final Set CLAIM_STATUSES = Collections.unmodifiableSet(
            new HashSet(Arrays.asList(new String[] {
                "OPEN", "INVESTIGATING", "APPROVED", "DENIED", "CLOSED" })));

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        String status = request.getParameter("status");
        if (status == null || status.length() == 0) {
            status = "INVESTIGATING";
        }
        status = status.trim().toUpperCase();
        if (!CLAIM_STATUSES.contains(status)) {
            log.warn("Rejected unknown status for claim " + claimId);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Unknown claim status");
            return null;
        }
        new ClaimDAO().updateStatus(claimId, status);
        request.setAttribute("claim", findClaim(claimId));
        request.setAttribute("claimStatus", status);
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
