package com.northstar.claims.web;

import java.util.ArrayList;
import java.util.List;
import com.northstar.claims.dao.PolicyDAO;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the PolicySearchAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class PolicySearchAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!canReadPortfolio(request)) {
            return denied(mapping, request);
        }
        String line = request.getParameter("lineOfBusiness");
        if (line == null || line.length() == 0) {
            line = "AUTO";
        }
        List policies;
        try {
            policies = new PolicyDAO().findByLine(line);
        } catch (Exception failure) {
            log.warn("Policy search failed", failure);
            policies = new ArrayList();
        }
        request.setAttribute("policies", policies);
        request.setAttribute("searchLine", line);
        request.setAttribute("searchCount", new Integer(policies.size()));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("policySearch");
    }
}
