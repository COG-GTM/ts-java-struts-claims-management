package com.northstar.payment.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northstar.payment.persistence.PaymentRepository;
import com.northstar.payment.persistence.SettlementLookupRepository;
import com.northstar.payment.persistence.SettlementLookupRepository.ClaimSettlement;
import com.northstar.payment.persistence.StoredPayment;

/**
 * The four payment actions of struts-config.xml:223-239,340-356 as one
 * controller (ADR-002). Field names are the {@code <ns:field>} names of the
 * payment JSPs; the forward name is the {@code screen}.
 *
 * <ul>
 *   <li>PAY-R01, PAY-R14 — {@code POST /payment/issue} answers
 *       {@code paymentAmount}, {@code checkNumber}, {@code paymentStatus}
 *       (src/main/webapp/WEB-INF/jsp/payment/issue.jsp:19-23);</li>
 *   <li>PAY-R16, PAY-R18 — {@code GET /payment/history} answers three fields
 *       per payment, suffixed with the payment id (history.jsp:26-32);</li>
 *   <li>PAY-R20, PAY-R23 — {@code GET /payment/detail} answers the six
 *       {@code detailPayment*} fields (detail.jsp:10-24);</li>
 *   <li>PAY-R24, PAY-R25, PAY-R26 — {@code GET /payment/remittance} answers
 *       four fields per payment plus {@code remitClaimId}, {@code remitCount},
 *       {@code remitTotal} (remittance.jsp:16-32).</li>
 * </ul>
 */
@RestController
public class PaymentController {

    private final PaymentRepository payments;
    private final SettlementLookupRepository settlements;

    public PaymentController(PaymentRepository payments, SettlementLookupRepository settlements) {
        this.payments = payments;
        this.settlements = settlements;
    }

    /** PaymentIssueAction.java:20-43. */
    @PostMapping("/payment/issue")
    public PaymentResponse issue(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String payeeName,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String paymentMethod) {
        int claim = LegacyParameters.integer(claimId, LegacyParameters.DEFAULT_CLAIM_ID);
        ClaimSettlement settlement = settlements.findLatestByClaim(claim)
                .orElseThrow(() -> new MissingSettlementException(claim));
        int paymentId = payments.nextId();
        StoredPayment payment = new StoredPayment(paymentId, claim, settlement.settlementId(),
                payeeName,
                LegacyParameters.decimal(amount, settlement.settlementAmount()),
                paymentMethod,
                LegacyParameters.checkNumber(paymentId),
                LegacyParameters.ISSUED_DATE,
                LegacyParameters.ISSUED_STATUS);
        payments.insert(payment);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("paymentAmount", FieldFormatter.money(payment.amount()));
        fields.put("checkNumber", FieldFormatter.text(payment.checkNumber()));
        fields.put("paymentStatus", FieldFormatter.text(payment.status()));
        return PaymentResponse.of("issue", fields);
    }

    /** PaymentHistoryAction.java:20-26. */
    @GetMapping("/payment/history")
    public PaymentResponse history(@RequestParam(required = false) String claimId) {
        int claim = LegacyParameters.integer(claimId, LegacyParameters.DEFAULT_CLAIM_ID);
        Map<String, String> fields = new LinkedHashMap<>();
        for (StoredPayment payment : payments.findByClaim(claim)) {
            String id = String.valueOf(payment.paymentId());
            fields.put("checkNumber_" + id, FieldFormatter.text(payment.checkNumber()));
            fields.put("paymentAmount_" + id, FieldFormatter.money(payment.amount()));
            fields.put("paymentStatus_" + id, FieldFormatter.text(payment.status()));
        }
        return PaymentResponse.of("history", fields);
    }

    /** PaymentDetailAction.java:17-24. */
    @GetMapping("/payment/detail")
    public PaymentResponse detail(@RequestParam(required = false) String paymentId) {
        int id = LegacyParameters.integer(paymentId, LegacyParameters.DEFAULT_PAYMENT_ID);
        StoredPayment payment = payments.findById(id)
                .orElseThrow(() -> new MissingPaymentException(id));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("detailPaymentId", FieldFormatter.integer(payment.paymentId()));
        fields.put("detailPaymentClaim", FieldFormatter.integer(payment.claimId()));
        fields.put("detailPaymentAmount", FieldFormatter.money(payment.amount()));
        fields.put("detailPaymentCheck", FieldFormatter.text(payment.checkNumber()));
        fields.put("detailPaymentDate", FieldFormatter.date(payment.issuedDate()));
        fields.put("detailPaymentStatus", FieldFormatter.text(payment.status()));
        return PaymentResponse.of("detail", fields);
    }

    /** PaymentRemittanceAction.java:17-24. */
    @GetMapping("/payment/remittance")
    public PaymentResponse remittance(@RequestParam(required = false) String claimId) {
        int claim = LegacyParameters.integer(claimId, LegacyParameters.DEFAULT_CLAIM_ID);
        List<StoredPayment> rows = payments.findByClaim(claim);
        Map<String, String> fields = new LinkedHashMap<>();
        for (StoredPayment payment : rows) {
            String id = String.valueOf(payment.paymentId());
            fields.put("remitCheck_" + id, FieldFormatter.text(payment.checkNumber()));
            fields.put("remitDate_" + id, FieldFormatter.date(payment.issuedDate()));
            fields.put("remitAmount_" + id, FieldFormatter.money(payment.amount()));
            fields.put("remitStatus_" + id, FieldFormatter.text(payment.status()));
        }
        fields.put("remitClaimId", FieldFormatter.integer(claim));
        fields.put("remitCount", FieldFormatter.integer(rows.size()));
        fields.put("remitTotal", FieldFormatter.money(payments.totalIssued(claim)));
        return PaymentResponse.of("remittance", fields);
    }
}
