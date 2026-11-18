package com.northstar.claims.web;

import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.model.Payment;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the paymentDetail screen data and selects its configured forward. */
public class PaymentDetailAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int id = integer(request.getParameter("paymentId"), 61);
        Payment payment = new PaymentDAO().findById(id);
        request.setAttribute("payment", payment);
        request.setAttribute("claimId", Integer.valueOf(payment == null ? 119 : payment.getClaimId()));
        request.setAttribute("screenName", "detail");
        request.setAttribute("screenMode", "read");
        request.setAttribute("operatorScope", "claims");
        return mapping.findForward("paymentDetail");
    }
}
