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
 * Handles the PolicyListAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class PolicyListAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!canReadPortfolio(request)) {
            return denied(mapping, request);
        }
        List policies;
        try {
            policies = new PolicyDAO().findAll();
        } catch (Exception failure) {
            log.warn("Policy list failed", failure);
            policies = new ArrayList();
        }
        request.setAttribute("policies", policies);
        request.setAttribute("policyCount", new Integer(policies.size()));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("policyList");
    }
}
