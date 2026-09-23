package com.northstar.claims.web;

import com.northstar.claims.model.Claim;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the WorkbenchNoteAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class WorkbenchNoteAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        Claim claim = authorizedClaim(request, claimId);
        if (claim == null) {
            return denied(mapping, request);
        }
        String note = request.getParameter("noteText");
        if (note == null || note.length() == 0) {
            note = "Review completed";
        }
        request.setAttribute("claim", claim);
        request.setAttribute("noteText", note);
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchView");
    }
}
