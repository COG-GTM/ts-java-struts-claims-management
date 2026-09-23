package com.northstar.claims.web;

import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.model.Policy;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the coverageDetail screen data and selects its configured forward. */
public class PolicyCoverageDetailAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int id = integer(request.getParameter("policyId"), 9001);
        if (!canAccessPolicy(request, id)) {
            return denied(mapping, request);
        }
        Policy policy = new PolicyDAO().findById(id);
        request.setAttribute("policy", policy);
        request.setAttribute("coverages", new java.util.ArrayList());
        request.setAttribute("screenName", "detail");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        return mapping.findForward("coverageDetail");
    }
}
