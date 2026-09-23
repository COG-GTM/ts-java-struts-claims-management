package com.northstar.claims.web;

import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.model.Payment;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the remittance screen data and selects its configured forward. */
public class PaymentRemittanceAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int id = integer(request.getParameter("claimId"), 119);
        if (authorizedClaim(request, id) == null) {
            return denied(mapping, request);
        }
        java.util.List rows = new PaymentDAO().findByClaim(id);
        request.setAttribute("payments", rows);
        request.setAttribute("claimId", new Integer(id));
        request.setAttribute("paymentCount", new Integer(rows.size()));
        request.setAttribute("paymentTotal", new Double(new PaymentDAO().totalIssued(id)));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("remittance");
    }
}
