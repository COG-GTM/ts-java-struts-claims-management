package com.northstar.claims.web;

import com.northstar.claims.dao.SettlementDAO;
import com.northstar.claims.model.Settlement;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the settlementDetail screen data and selects its configured forward. */
public class SettlementDetailAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int id = integer(request.getParameter("claimId"), 119);
        if (authorizedClaim(request, id) == null) {
            return denied(mapping, request);
        }
        Settlement settlement = new SettlementDAO().findByClaim(id);
        request.setAttribute("settlement", settlement);
        request.setAttribute("screenName", "detail");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        return mapping.findForward("settlementDetail");
    }
}
