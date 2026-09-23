package com.northstar.claims.web;

import java.util.List;
import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.model.Policy;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the PolicyCoveragesAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class PolicyCoveragesAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int policyId = integer(request.getParameter("policyId"), 1);
        if (!canAccessPolicy(request, policyId)) {
            return denied(mapping, request);
        }
        Policy policy = new PolicyDAO().findById(policyId);
        List coverages = new java.util.ArrayList();
        request.setAttribute("policy", policy);
        request.setAttribute("coverages", coverages);
        request.setAttribute("coverageCount", new Integer(coverages.size()));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("coverages");
    }
}
