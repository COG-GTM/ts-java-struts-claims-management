package com.northstar.claims.web;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the IntakeConfirmAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class IntakeConfirmAction extends ClaimsActionSupport {

    /** Handles the confirmation request separately from the submission. */

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        request.setAttribute("confirmationMode", "READ_ONLY");
        Integer claimId = claimIdentifier(request.getParameter("claimId"));
        if (claimId != null) {
            request.setAttribute("claimId", claimId);
        }
        request.setAttribute("screenName", "detail");
        return mapping.findForward("confirm");
    }

    /** Accepts only a plain claim identifier; anything else is discarded. */
    Integer claimIdentifier(String requested) {
        if (requested == null || !requested.matches("\\d{1,9}")) {
            return null;
        }
        return new Integer(requested);
    }

}
