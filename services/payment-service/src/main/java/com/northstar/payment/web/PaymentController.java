package com.northstar.payment.web;

import com.northstar.payment.persistence.PaymentRepository;
import com.northstar.payment.persistence.SettlementRepository;
import com.northstar.payment.persistence.SettlementRepository.NewestSettlement;
import com.northstar.payment.persistence.StoredPayment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four payment flows of the monolith (src/main/webapp/WEB-INF/struts-config.xml:223-238,340-355)
 * with the same parameter names and the {@code <ns:field>} names of
 * issue.jsp, history.jsp, detail.jsp and remittance.jsp. Implements
 * docs/specs/SPEC-PAY-001.md version 0.1 as written, including behaviour that
 * looks wrong; see ADR-002 for the mapping.
 */
@RestController
@RequestMapping("/payment")
public class PaymentController {
    private static final String ISSUED_DATE = "2019-04-03";
    private static final String ISSUED = "ISSUED";

    private final PaymentRepository payments;
    private final SettlementRepository settlements;

    public PaymentController(PaymentRepository payments, SettlementRepository settlements) {
        this.payments = payments;
        this.settlements = settlements;
    }

    /** PaymentIssueAction.java:22-43; GET writes too, as the Struts mapping does. */
    @RequestMapping(value = "/issue", method = {RequestMethod.GET, RequestMethod.POST})
    public PaymentResponse issue(@RequestHeader(OperatorFilter.HEADER) String operator,
            @RequestParam(required = false) String claimId,
            @RequestParam(required = false) String payeeName,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String paymentMethod) {
        int claim = LegacyParameters.integer(claimId, 119);
        NewestSettlement settlement = settlements.findNewestByClaim(claim)
                .orElseThrow(() -> new PaymentFailure("no settlement for claim " + claim));
        int paymentId = payments.nextId();
        StoredPayment payment = new StoredPayment(paymentId, claim, settlement.settlementId(),
                payeeName, LegacyParameters.decimal(amount, settlement.settlementAmount()),
                paymentMethod, "CHK-" + paymentId, ISSUED_DATE, ISSUED);
        payments.insert(payment);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("paymentAmount", FieldFormatter.money(payment.amount()));
        fields.put("checkNumber", FieldFormatter.text(payment.checkNumber()));
        fields.put("paymentStatus", FieldFormatter.text(payment.status()));
        return new PaymentResponse("issue", fields, Map.of(), List.of(), List.of(), operator);
    }

    /** PaymentHistoryAction.java:20-26; history.jsp:16-32. */
    @GetMapping("/history")
    public PaymentResponse history(@RequestHeader(OperatorFilter.HEADER) String operator,
            @RequestParam(required = false) String claimId) {
        int claim = LegacyParameters.integer(claimId, 119);
        Map<String, String> fields = new LinkedHashMap<>();
        for (StoredPayment payment : payments.findByClaim(claim)) {
            int id = payment.paymentId();
            fields.put("checkNumber_" + id, FieldFormatter.text(payment.checkNumber()));
            fields.put("paymentAmount_" + id, FieldFormatter.money(payment.amount()));
            fields.put("paymentStatus_" + id, FieldFormatter.text(payment.status()));
        }
        String detail = "detail.do?paymentId=61";
        String remittance = "remittance.do?claimId=" + claim;
        return new PaymentResponse("history", fields, Map.of(),
                List.of(detail, remittance, detail, remittance), List.of(), operator);
    }

    /** PaymentDetailAction.java:17-24; detail.jsp:8-25. */
    @GetMapping("/detail")
    public PaymentResponse detail(@RequestHeader(OperatorFilter.HEADER) String operator,
            @RequestParam(required = false) String paymentId) {
        int id = LegacyParameters.integer(paymentId, 61);
        StoredPayment payment = payments.findById(id)
                .orElseThrow(() -> new PaymentFailure("no payment " + id));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("detailPaymentId", FieldFormatter.integer(payment.paymentId()));
        fields.put("detailPaymentClaim", FieldFormatter.integer(payment.claimId()));
        fields.put("detailPaymentAmount", FieldFormatter.money(payment.amount()));
        fields.put("detailPaymentCheck", FieldFormatter.text(payment.checkNumber()));
        fields.put("detailPaymentDate", FieldFormatter.date(payment.issuedDate()));
        fields.put("detailPaymentStatus", FieldFormatter.text(payment.status()));
        Map<String, String> display = new LinkedHashMap<>();
        display.put("payeeName", FieldFormatter.text(payment.payeeName()));
        display.put("paymentMethod", FieldFormatter.text(payment.paymentMethod()));
        return new PaymentResponse("detail", fields, display, List.of(), List.of(), operator);
    }

    /** PaymentRemittanceAction.java:17-24; remittance.jsp:9-33. */
    @GetMapping("/remittance")
    public PaymentResponse remittance(@RequestHeader(OperatorFilter.HEADER) String operator,
            @RequestParam(required = false) String claimId) {
        int claim = LegacyParameters.integer(claimId, 119);
        List<StoredPayment> rows = payments.findByClaim(claim);
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (StoredPayment payment : rows) {
            int id = payment.paymentId();
            fields.put("remitCheck_" + id, FieldFormatter.text(payment.checkNumber()));
            display.put("remitPayee_" + id, FieldFormatter.text(payment.payeeName()));
            fields.put("remitDate_" + id, FieldFormatter.date(payment.issuedDate()));
            fields.put("remitAmount_" + id, FieldFormatter.money(payment.amount()));
            fields.put("remitStatus_" + id, FieldFormatter.text(payment.status()));
        }
        fields.put("remitClaimId", FieldFormatter.integer(claim));
        fields.put("remitCount", FieldFormatter.integer(rows.size()));
        fields.put("remitTotal", FieldFormatter.money(payments.totalIssued(claim)));
        return new PaymentResponse("remittance", fields, display, List.of(), List.of(), operator);
    }
}
