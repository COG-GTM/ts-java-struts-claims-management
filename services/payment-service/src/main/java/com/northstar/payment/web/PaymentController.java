package com.northstar.payment.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northstar.payment.persistence.PaymentRepository;
import com.northstar.payment.persistence.SettlementLookupRepository;
import com.northstar.payment.persistence.SettlementLookupRepository.ClaimSettlement;
import com.northstar.payment.persistence.StoredPayment;

/**
 * The four payment actions of src/main/webapp/WEB-INF/struts-config.xml:223-238,
 * 340-355 as one controller (ADR-002). Field names are the {@code <ns:field>}
 * names of the payment JSPs; the forward name is the {@code screen}.
 *
 * <ul>
 *   <li>PAY-R02, PAY-R03, PAY-R17 — {@code /payment/issue} (POST or GET) answers
 *       {@code paymentAmount}, {@code checkNumber}, {@code paymentStatus}
 *       (src/main/webapp/WEB-INF/jsp/payment/issue.jsp:19-23);</li>
 *   <li>PAY-R18, PAY-R19, PAY-R21 — {@code GET /payment/history} answers three
 *       fields per payment, suffixed with the payment id, and nothing else
 *       (history.jsp:26-32);</li>
 *   <li>PAY-R22, PAY-R24 — {@code GET /payment/detail} answers the six
 *       {@code detailPayment*} fields (detail.jsp:10-24);</li>
 *   <li>PAY-R25, PAY-R26, PAY-R27 — {@code GET /payment/remittance} answers four
 *       fields per payment plus {@code remitClaimId}, {@code remitCount},
 *       {@code remitTotal} (remittance.jsp:16-32).</li>
 * </ul>
 *
 * The payee and method cells of detail.jsp:14,18 and remittance.jsp:18 are
 * plain {@code <bean:write>} output without an {@code <ns:field>} id, so they
 * are not business fields and are not answered (ADR-002, "How parity is
 * judged"). The screen-audit table (PAY-R30) and the links of PAY-R20 are
 * presentation and are not reproduced.
 */
@RestController
public class PaymentController {

    private final PaymentRepository payments;
    private final SettlementLookupRepository settlements;

    public PaymentController(PaymentRepository payments, SettlementLookupRepository settlements) {
        this.payments = payments;
        this.settlements = settlements;
    }

    /**
     * PaymentIssueAction.java:22-43. PAY-R03: the Struts mapping is not
     * restricted to POST, so GET issues a payment too.
     */
    @RequestMapping(path = "/payment/issue", method = {RequestMethod.POST, RequestMethod.GET})
    public PaymentResponse issue(
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String payeeName,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String paymentMethod) {
        // PAY-R04: PaymentIssueAction.java:22
        int claim = LegacyParameters.integer(claimId, LegacyParameters.DEFAULT_CLAIM_ID);
        // PAY-R05: PaymentIssueAction.java:23,28 - the newest settlement, or nothing
        ClaimSettlement settlement = settlements.findLatestByClaim(claim)
                .orElseThrow(() -> new MissingSettlementException(claim));
        // PAY-R14: PaymentIssueAction.java:25,33
        int paymentId = payments.nextId();
        StoredPayment payment = new StoredPayment(paymentId, claim, settlement.settlementId(),
                // PAY-R10: PaymentIssueAction.java:29,32 - stored exactly as sent
                payeeName,
                // PAY-R06, PAY-R07, PAY-R08: PaymentIssueAction.java:30-31
                LegacyParameters.decimal(amount, settlement.settlementAmount()),
                paymentMethod,
                LegacyParameters.checkNumber(paymentId),
                // PAY-R15: PaymentIssueAction.java:34-35
                LegacyParameters.ISSUED_DATE,
                LegacyParameters.ISSUED_STATUS);
        // PAY-R16, PAY-R12: PaymentIssueAction.java:36 - one insert, checked only by the schema
        payments.insert(payment);
        // PAY-R17: issue.jsp:19-23 - paymentId and claimId are set but not rendered
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("paymentAmount", FieldFormatter.money(payment.amount()));
        fields.put("checkNumber", FieldFormatter.text(payment.checkNumber()));
        fields.put("paymentStatus", FieldFormatter.text(payment.status()));
        return PaymentResponse.of("issue", fields);
    }

    /** PaymentHistoryAction.java:20-26; PAY-R18, PAY-R19, PAY-R21. */
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
        // PAY-R21: paymentCount is set by the action but not rendered by history.jsp
        return PaymentResponse.of("history", fields);
    }

    /** PaymentDetailAction.java:17-24; PAY-R22, PAY-R23, PAY-R24. */
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

    /** PaymentRemittanceAction.java:17-24; PAY-R25, PAY-R26, PAY-R27. */
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
