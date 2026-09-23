package com.northstar.claims.web;

import java.util.ArrayList;
import java.util.List;
import com.northstar.claims.dao.ClaimDAO;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the WorkbenchListAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class WorkbenchListAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        List claims;
        try {
            claims = visibleClaims(request,
                    new ClaimDAO().findByStatus("OPEN"));
        } catch (Exception failure) {
            claims = new ArrayList();
            log.warn("Open claim workbench failed", failure);
        }
        request.setAttribute("claims", claims);
        request.setAttribute("claimCount", new Integer(claims.size()));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("workbenchList");
    }
}
