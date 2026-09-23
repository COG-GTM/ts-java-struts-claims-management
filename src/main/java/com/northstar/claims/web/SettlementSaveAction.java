package com.northstar.claims.web;

import com.northstar.claims.dao.PolicyDAO;
import com.northstar.claims.dao.SettlementDAO;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Policy;
import com.northstar.claims.model.Settlement;
import com.northstar.claims.service.SettlementCalculator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the SettlementSaveAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class SettlementSaveAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        Claim claim = authorizedClaim(request, claimId);
        if (claim == null) {
            return denyClaimAccess(mapping, request, response, claimId);
        }
        Policy policy = new PolicyDAO().findById(claim.getPolicyId());
        double covered = decimal(request.getParameter("coveredAmount"), 5000);
        double depreciation = decimal(request.getParameter("depreciation"), 0);
        String deductible = request.getParameter("deductible");
        Settlement value = SettlementCalculator.getInstance().calculate(
                covered, deductible == null || deductible.length() == 0
                        ? "0" : deductible, depreciation,
                policy.getPolicyLimit());
        value.setSettlementId(nextId("SETTLEMENT"));
        value.setClaimId(claimId);
        value.setCalculatedBy(String.valueOf(
                request.getSession().getAttribute("user")));
        value.setCalculatedDate("2019-04-01");
        new SettlementDAO().save(value);
        request.setAttribute("settlement", value);
        request.setAttribute("settlementAmount",
                new Double(value.getSettlementAmount()));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("settlement");
    }
}
