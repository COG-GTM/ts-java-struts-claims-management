package com.northstar.claims.web.payment;

import com.northstar.claims.domain.Payment;
import com.northstar.claims.repository.PaymentDAO;
import com.northstar.claims.repository.SettlementDAO;
import com.northstar.claims.support.ClaimsSupport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Ports the request-scoped payment actions without adding validation. */
@Controller
public class PaymentController {
    private final PaymentDAO payments;
    private final SettlementDAO settlements;
    private final ClaimsSupport support;

    public PaymentController(PaymentDAO payments, SettlementDAO settlements, ClaimsSupport support) {
        this.payments = payments;
        this.settlements = settlements;
        this.support = support;
    }

    @RequestMapping("/payment/issue.do")
    public String issue(@RequestParam(required = false) String claimId,
            @RequestParam(required = false) String payeeName,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String paymentMethod, Model model) throws Exception {
        int id = support.integer(claimId, 119);
        var settlement = settlements.findByClaim(id);
        Payment payment = new Payment();
        int paymentId = support.nextId("PAYMENT");
        payment.setPaymentId(paymentId);
        payment.setClaimId(id);
        // A missing settlement deliberately reaches the legacy global error forward.
        payment.setSettlementId(settlement.getSettlementId());
        payment.setPayeeName(payeeName);
        payment.setAmount(support.decimal(amount, settlement.getSettlementAmount()));
        payment.setPaymentMethod(paymentMethod);
        payment.setCheckNumber("CHK-" + paymentId);
        payment.setIssuedDate("2019-04-03");
        payment.setStatus("ISSUED");
        payments.insert(payment);
        model.addAttribute("paymentId", paymentId);
        model.addAttribute("claimId", id);
        model.addAttribute("paymentAmount", payment.getAmount());
        model.addAttribute("checkNumber", payment.getCheckNumber());
        model.addAttribute("paymentStatus", payment.getStatus());
        model.addAttribute("screenName", "detail");
        return "payment/issue";
    }

    @RequestMapping("/payment/history.do")
    public String history(@RequestParam(required = false) String claimId, Model model) throws Exception {
        loadPayments(claimId, model);
        return "payment/history";
    }

    @RequestMapping("/payment/detail.do")
    public String detail(@RequestParam(required = false) String paymentId, Model model) throws Exception {
        var payment = payments.findById(support.integer(paymentId, 61));
        // JSP bean:write fails when the requested payment does not exist.
        if (payment == null) return "error";
        model.addAttribute("payment", payment);
        model.addAttribute("claimId", payment.getClaimId());
        model.addAttribute("screenName", "detail");
        model.addAttribute("screenMode", "read");
        model.addAttribute("operatorScope", "claims");
        return "payment/detail";
    }

    @RequestMapping("/payment/remittance.do")
    public String remittance(@RequestParam(required = false) String claimId, Model model) throws Exception {
        int id = loadPayments(claimId, model);
        // The DAO sums all statuses, despite the legacy totalIssued name.
        model.addAttribute("paymentTotal", payments.totalIssued(id));
        return "payment/remittance";
    }

    private int loadPayments(String claimId, Model model) throws Exception {
        int id = support.integer(claimId, 119);
        var rows = payments.findByClaim(id);
        model.addAttribute("payments", rows);
        model.addAttribute("claimId", id);
        model.addAttribute("paymentCount", rows.size());
        model.addAttribute("screenName", "detail");
        return id;
    }
}
