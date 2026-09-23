package com.northstar.claims.web;

import com.northstar.claims.dao.PaymentDAO;
import com.northstar.claims.dao.SettlementDAO;
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
 * The action loads screen data and completes the configured request workflow.
 */
public class PaymentIssueAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return refuse(mapping, request, response, "payment.issue.method");
        }
        int claimId = integer(request.getParameter("claimId"), 119);
        String operator = currentOperator(request);
        String role = operatorRole(request, operator);
        Claim claim = findClaim(claimId);
        if (!PaymentAuthorization.canIssuePayment(operator, role, claim)) {
            log.warn("Refusing payment issue on claim " + claimId
                    + " for operator " + operator + " with role " + role);
            return refuse(mapping, request, response, "payment.issue.denied");
        }
        Settlement settlement = new SettlementDAO().findByClaim(claimId);
        if (settlement == null) {
            return refuse(mapping, request, response,
                    "payment.issue.noSettlement");
        }
        double amount = decimal(request.getParameter("amount"),
                settlement.getSettlementAmount());
        if (amount <= 0.0 || !financialAmount(amount)) {
            return refuse(mapping, request, response, "payment.issue.amount");
        }
        Payment payment = new Payment();
        int paymentId = nextId("PAYMENT");
        payment.setPaymentId(paymentId);
        payment.setClaimId(claimId);
        payment.setSettlementId(settlement.getSettlementId());
        payment.setPayeeName(request.getParameter("payeeName"));
        payment.setAmount(amount);
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

    private String operatorRole(HttpServletRequest request, String operator) {
        Object value = request.getSession().getAttribute("role");
        if (value != null) {
            return String.valueOf(value);
        }
        return PaymentAuthorization.roleFor(operator);
    }

    private ActionForward refuse(ActionMapping mapping,
            HttpServletRequest request, HttpServletResponse response,
            String messageKey) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("message", messageKey);
        request.setAttribute("paymentAmount", new Double(0));
        request.setAttribute("checkNumber", "");
        request.setAttribute("paymentStatus", "REFUSED");
        request.setAttribute("screenName", "detail");
        return mapping.findForward("payment");
    }
}
