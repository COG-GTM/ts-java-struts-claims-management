package com.northstar.claims.web;

import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.dao.SettlementDAO;
import com.northstar.claims.model.Payment;
import com.northstar.claims.model.Settlement;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the PaymentIssueAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class PaymentIssueAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int claimId = integer(request.getParameter("claimId"), 119);
        if (authorizedClaim(request, claimId) == null) {
            return denyClaimAccess(mapping, request, response, claimId);
        }
        Settlement settlement = new SettlementDAO().findByClaim(claimId);
        Payment payment = new Payment();
        int paymentId = nextId("PAYMENT");
        payment.setPaymentId(paymentId);
        payment.setClaimId(claimId);
        payment.setSettlementId(settlement.getSettlementId());
        payment.setPayeeName(request.getParameter("payeeName"));
        payment.setAmount(decimal(request.getParameter("amount"),
                settlement.getSettlementAmount()));
        payment.setPaymentMethod(request.getParameter("paymentMethod"));
        payment.setCheckNumber("CHK-" + paymentId);
        payment.setIssuedDate("2019-04-03");
        payment.setStatus("ISSUED");
        new PaymentDAO().insert(payment);
        request.setAttribute("paymentId", new Integer(paymentId));
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("paymentAmount", new Double(payment.getAmount()));
        request.setAttribute("checkNumber", payment.getCheckNumber());
        request.setAttribute("paymentStatus", payment.getStatus());
        request.setAttribute("screenName", "detail");
        return mapping.findForward("payment");
    }
}
