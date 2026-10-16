package com.northstar.payment.web;

import com.northstar.payment.persistence.PaymentRepository;
import com.northstar.payment.persistence.SettlementRepository;
import com.northstar.payment.persistence.SettlementRepository.LatestSettlement;
import com.northstar.payment.persistence.StoredPayment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four payment request flows of the monolith, with the same request
 * parameter names as the Struts screens and the field names of the
 * {@code <span id="f_...">} values on issue.jsp, history.jsp, detail.jsp and
 * remittance.jsp (src/main/webapp/WEB-INF/struts-config.xml:223-231).
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R01 — {@code /payment/issue} takes claimId, payeeName, amount and
 *       paymentMethod and answers paymentAmount, checkNumber and paymentStatus
 *       (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-36;
 *       src/main/webapp/WEB-INF/jsp/payment/issue.jsp).</li>
 *   <li>PAY-R02, PAY-R04, PAY-R05, PAY-R16 — parameter coercion, delegated to
 *       {@link LegacyParameters}.</li>
 *   <li>PAY-R03 — the newest settlement of the claim (PaymentIssueAction.java:23,28).</li>
 *   <li>PAY-R06 — payment_id is {@code max(payment_id)+1} and one row is
 *       inserted (PaymentIssueAction.java:25-26,36).</li>
 *   <li>PAY-R07 — check_number is {@code "CHK-" + paymentId}; a checkNumber
 *       parameter is never read (PaymentIssueAction.java:33).</li>
 *   <li>PAY-R08 — issued_date 2019-04-03 and status ISSUED, literally
 *       (PaymentIssueAction.java:34-35).</li>
 *   <li>PAY-R09 — payeeName and paymentMethod are stored exactly as sent
 *       (PaymentIssueAction.java:29,32).</li>
 *   <li>PAY-R10, PAY-R11 — insert failures and a missing settlement reach the
 *       error screen through {@link PaymentErrorHandler}.</li>
 *   <li>PAY-R12 — issue is mapped for GET as well as POST and checks neither
 *       the claim status nor the operator's role (struts-config.xml:223-231;
 *       PaymentIssueAction.java:22-36).</li>
 *   <li>PAY-R14 — history lists the claim's payments in payment_id order as
 *       checkNumber_, paymentAmount_ and paymentStatus_ suffixed by the id
 *       (PaymentHistoryAction.java:20-26; history.jsp).</li>
 *   <li>PAY-R15 — history's two links are always detail.do?paymentId=61 and
 *       remittance.do?claimId=&lt;claimId&gt; (history.jsp:16-19).</li>
 *   <li>PAY-R16, PAY-R17, PAY-R18 — detail (PaymentDetailAction.java:17-24;
 *       detail.jsp).</li>
 *   <li>PAY-R19, PAY-R20 — remittance rows, remitClaimId, remitCount and
 *       remitTotal (PaymentRemittanceAction.java:17-24; remittance.jsp;
 *       PaymentDAO.java:121-136).</li>
 * </ul>
 * PAY-R13 (the AuthFilter redirect) is the monolith's session boundary and is
 * out of this service's scope per ADR-002; the service has no login.
 */
@RestController
@RequestMapping("/payment")
public class PaymentController {
    private final PaymentRepository payments;
    private final SettlementRepository settlements;

    public PaymentController(PaymentRepository payments, SettlementRepository settlements) {
        this.payments = payments;
        this.settlements = settlements;
    }

    /** PaymentIssueAction.java:22-36. Mapped for GET too (PAY-R12). */
    @RequestMapping(value = "/issue", method = {
            org.springframework.web.bind.annotation.RequestMethod.GET,
            org.springframework.web.bind.annotation.RequestMethod.POST})
    public PaymentResponse issue(@RequestParam(required = false) String claimId,
            @RequestParam(required = false) String payeeName,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String paymentMethod) {
        int claim = LegacyParameters.integer(claimId, 119);
        LatestSettlement settlement = settlements.findLatestByClaim(claim)
                .orElseThrow(() -> new MissingSettlementException(claim));
        int paymentId = payments.nextId();
        StoredPayment payment = new StoredPayment(paymentId, claim, settlement.settlementId(),
                payeeName, LegacyParameters.decimal(amount, settlement.settlementAmount()),
                paymentMethod, "CHK-" + paymentId, "2019-04-03", "ISSUED");
        payments.insert(payment);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("paymentAmount", FieldFormatter.money(payment.amount()));
        fields.put("checkNumber", FieldFormatter.text(payment.checkNumber()));
        fields.put("paymentStatus", FieldFormatter.text(payment.status()));
        return PaymentResponse.of("issue", fields);
    }

    /** PaymentHistoryAction.java:20-26; history.jsp. */
    @GetMapping("/history")
    public PaymentResponse history(@RequestParam(required = false) String claimId) {
        int claim = LegacyParameters.integer(claimId, 119);
        Map<String, String> fields = new LinkedHashMap<>();
        for (StoredPayment payment : payments.findByClaim(claim)) {
            String id = Integer.toString(payment.paymentId());
            fields.put("checkNumber_" + id, FieldFormatter.text(payment.checkNumber()));
            fields.put("paymentAmount_" + id, FieldFormatter.money(payment.amount()));
            fields.put("paymentStatus_" + id, FieldFormatter.text(payment.status()));
        }
        List<String> links = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            links.add("detail.do?paymentId=61");
            links.add("remittance.do?claimId=" + claim);
        }
        return PaymentResponse.of("history", fields, Map.of(), links);
    }

    /** PaymentDetailAction.java:17-24; detail.jsp. */
    @GetMapping("/detail")
    public PaymentResponse detail(@RequestParam(required = false) String paymentId) {
        int id = LegacyParameters.integer(paymentId, 61);
        StoredPayment payment = payments.findById(id)
                .orElseThrow(() -> new MissingPaymentException(id));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("detailPaymentId", FieldFormatter.integer(payment.paymentId()));
        fields.put("detailPaymentClaim", FieldFormatter.integer(payment.claimId()));
        fields.put("detailPaymentAmount", FieldFormatter.money(payment.amount()));
        fields.put("detailPaymentCheck", FieldFormatter.text(payment.checkNumber()));
        fields.put("detailPaymentDate", FieldFormatter.date(payment.issuedDate()));
        fields.put("detailPaymentStatus", FieldFormatter.text(payment.status()));
        Map<String, String> display = new LinkedHashMap<>();
        display.put("payee", FieldFormatter.escape(payment.payeeName()));
        display.put("method", FieldFormatter.escape(payment.paymentMethod()));
        return PaymentResponse.of("detail", fields, display, List.of());
    }

    /** PaymentRemittanceAction.java:17-24; remittance.jsp. */
    @GetMapping("/remittance")
    public PaymentResponse remittance(@RequestParam(required = false) String claimId) {
        int claim = LegacyParameters.integer(claimId, 119);
        List<StoredPayment> rows = payments.findByClaim(claim);
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (StoredPayment payment : rows) {
            String id = Integer.toString(payment.paymentId());
            fields.put("remitCheck_" + id, FieldFormatter.text(payment.checkNumber()));
            display.put("payee_" + id, FieldFormatter.escape(payment.payeeName()));
            fields.put("remitDate_" + id, FieldFormatter.date(payment.issuedDate()));
            fields.put("remitAmount_" + id, FieldFormatter.money(payment.amount()));
            fields.put("remitStatus_" + id, FieldFormatter.text(payment.status()));
        }
        fields.put("remitClaimId", FieldFormatter.integer(claim));
        fields.put("remitCount", FieldFormatter.integer(rows.size()));
        fields.put("remitTotal", FieldFormatter.money(payments.totalIssued(claim)));
        return PaymentResponse.of("remittance", fields, display, List.of());
    }
}
