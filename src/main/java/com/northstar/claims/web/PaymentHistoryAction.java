package com.northstar.claims.web;

import java.util.List;
import com.northstar.claims.dao.PaymentDAO;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the PaymentHistoryAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class PaymentHistoryAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        List payments = new PaymentDAO().findByClaim(claimId);
        request.setAttribute("payments", payments);
        request.setAttribute("claimId", Integer.valueOf(claimId));
        request.setAttribute("paymentCount", Integer.valueOf(payments.size()));
        request.setAttribute("screenName", "detail");
        return mapping.findForward("payment");
    }
}
