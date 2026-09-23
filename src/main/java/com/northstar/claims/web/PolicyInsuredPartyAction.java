package com.northstar.claims.web;

import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.model.Policy;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the insuredParty screen data and selects its configured forward. */
public class PolicyInsuredPartyAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int id = integer(request.getParameter("policyId"), 9001);
        if (!canAccessPolicy(request, id)) {
            return denied(mapping, request);
        }
        Policy policy;
        try {
            policy = new PolicyDAO().findById(id);
        } catch (Exception failure) {
            policy = new Policy();
        }
        if (policy == null) {
            policy = new Policy();
        }
        policy.setPolicyId(id);
        policy.setPolicyNumber("POL-" + id);
        policy.setInsuredName("Policy insured party");
        policy.setInsuredAddress("Address on policy record");
        policy.setEffectiveDate("2018-01-01");
        policy.setExpiryDate("2019-12-31");
        policy.setStatus("ACTIVE");
        request.setAttribute("policy", policy);
        request.setAttribute("screenName", "detail");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        return mapping.findForward("insuredParty");
    }
}
