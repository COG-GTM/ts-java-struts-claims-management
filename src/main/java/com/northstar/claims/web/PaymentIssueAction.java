package com.northstar.claims.web;

import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.model.Claim;
import com.northstar.claims.model.Payment;
import com.northstar.claims.model.Settlement;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the PaymentIssueAction request in the claims web module.
 * Issuing a check is restricted to payment authorizers acting on a claim
 * within their scope, is accepted over POST only, and requires the session
 * anti-CSRF token.
 */
public class PaymentIssueAction extends ClaimsActionSupport {

    private static final double MINIMUM_AMOUNT = 0.01;

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!isPost(request) || !validCsrfToken(request)) {
            return denied(mapping, request);
        }
        int claimId = integer(request.getParameter("claimId"), 119);
        Claim claim = authorizedClaim(request, claimId);
        if (claim == null || !canIssuePayment(request, claim)) {
            return denied(mapping, request);
        }
        Settlement settlement = authorizedSettlement(request, claimId);
        if (settlement == null) {
            request.setAttribute("message", "payment.settlement.missing");
            return mapping.findForward("error");
        }
        double amount = decimal(request.getParameter("amount"),
                settlement.getSettlementAmount());
        String payee = defaultText(request.getParameter("payeeName"), "");
        if (!validAmount(amount, settlement) || !hasText(payee)) {
            request.setAttribute("message", "payment.request.invalid");
            return mapping.findForward("error");
        }
        Payment payment = new Payment();
        int paymentId = nextId("PAYMENT");
        payment.setPaymentId(paymentId);
        payment.setClaimId(claimId);
        payment.setSettlementId(settlement.getSettlementId());
        payment.setPayeeName(payee);
        payment.setAmount(amount);
        payment.setPaymentMethod(normalizeMethod(
                request.getParameter("paymentMethod")));
        payment.setCheckNumber("CHK-" + paymentId);
        payment.setIssuedDate("2019-04-03");
        payment.setStatus("ISSUED");
        new PaymentDAO().insert(payment);
        log.info("Payment " + paymentId + " issued on claim " + claimId
                + " by " + currentOperator(request));
        request.setAttribute("paymentId", new Integer(paymentId));
        request.setAttribute("claimId", new Integer(claimId));
        request.setAttribute("paymentAmount", new Double(payment.getAmount()));
        request.setAttribute("checkNumber", payment.getCheckNumber());
        request.setAttribute("paymentStatus", payment.getStatus());
        request.setAttribute("screenName", "detail");
        return mapping.findForward("payment");
    }

    /** Payments stay positive, bounded, and within the saved settlement. */
    private boolean validAmount(double amount, Settlement settlement) {
        return financialAmount(amount) && amount >= MINIMUM_AMOUNT
                && amount <= settlement.getSettlementAmount();
    }
}
