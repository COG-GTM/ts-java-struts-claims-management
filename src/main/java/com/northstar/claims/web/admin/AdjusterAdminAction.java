package com.northstar.claims.web.admin;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the AdjusterAdminAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class AdjusterAdminAction
        extends com.northstar.claims.web.ClaimsActionSupport {

    /** Executes the common workflow and returns the configured forward. */
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!canReadPortfolio(request)) {
            return denied(mapping, request);
        }
        java.util.List adjusters =
                new com.northstar.claims.dao.AdjusterDAO().findAll();
        request.setAttribute("adjusters", adjusters);
        request.setAttribute("adjusterCount",
                new Integer(adjusters.size()));
        return mapping.findForward("adjusters");
    }
}
