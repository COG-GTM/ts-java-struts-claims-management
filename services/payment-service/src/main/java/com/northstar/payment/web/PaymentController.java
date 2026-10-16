package com.northstar.payment.web;

import com.northstar.payment.persistence.LatestSettlement;
import com.northstar.payment.persistence.PaymentRepository;
import com.northstar.payment.persistence.StoredPayment;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four payment flows of the monolith
 * (src/main/webapp/WEB-INF/struts-config.xml:223-251), each accepting GET and
 * POST as the Struts mappings do. Parameters are read with
 * {@code getParameter}, as the actions do, so a repeated parameter counts by
 * its first value. {@code user} stands in for the session user (ADR-002, as
 * ADR-001 does for savedBy).
 *
 * <p>Implements SPEC-PAY-001:
 * <ul>
 *   <li>PAY-R01 — issue reads claimId, payeeName, amount and paymentMethod
 *       and answers with paymentAmount, checkNumber and paymentStatus.</li>
 *   <li>PAY-R02 — claimId missing or not an integer is 119.</li>
 *   <li>PAY-R03 — the payment carries the newest settlement of the claim.</li>
 *   <li>PAY-R04 — amount missing or not a double is that settlement's
 *       amount.</li>
 *   <li>PAY-R05 — any parseable amount is stored as sent: no sign, ceiling,
 *       precision or NaN check.</li>
 *   <li>PAY-R06, PAY-R07, PAY-R08 — id {@code max+1}, check "CHK-" + id,
 *       issued 2019-04-03, status ISSUED
 *       (src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-44).</li>
 *   <li>PAY-R09 — payee and method stored exactly as sent.</li>
 *   <li>PAY-R10, PAY-R11 — insert failure or no settlement: the error screen,
 *       nothing written ({@link PaymentErrorHandler}).</li>
 *   <li>PAY-R12 — no check of HTTP method, claim status or user role.</li>
 *   <li>PAY-R13 — no user: 302 to /claims/login.do before anything runs
 *       (src/main/java/com/northstar/claims/web/AuthFilter.java:37-50).</li>
 *   <li>PAY-R14, PAY-R15 — history rows in id order and the two fixed
 *       detail.do?paymentId=61 links, each followed by the claim's remittance
 *       link.</li>
 *   <li>PAY-R16, PAY-R17 — detail of paymentId (default 61); unknown id is
 *       the error screen.</li>
 *   <li>PAY-R18 — payee and method returned as stored; the JSON client does
 *       the escaping that bean:write does in the JSP.</li>
 *   <li>PAY-R19, PAY-R20 — remittance rows, count and the plain sum of every
 *       amount.</li>
 * </ul>
 */
@RestController
@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
public class PaymentController {

    static final String LOGIN = "/claims/login.do";
    static final String ISSUED_DATE = "2019-04-03";
    static final String ISSUED = "ISSUED";

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @RequestMapping("/payment/issue")
    public ResponseEntity<PaymentResponse> issue(HttpServletRequest request) {
        if (!signedIn(request.getParameter("user"))) {
            return redirectToLogin();
        }
        int claim = claimId(request);
        LatestSettlement settlement = payments.findLatestSettlement(claim)
                .orElseThrow(() -> new MissingPaymentDataException(
                        "no settlement for claim " + claim));
        int paymentId = payments.nextId();
        double amount = LegacyParameters.decimal(request.getParameter("amount"),
                settlement.settlementAmount());
        StoredPayment payment = new StoredPayment(paymentId, claim,
                settlement.settlementId(), request.getParameter("payeeName"), amount,
                request.getParameter("paymentMethod"), "CHK-" + paymentId, ISSUED_DATE, ISSUED);
        payments.insert(payment);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("paymentAmount", LegacyParameters.money(payment.amount()));
        fields.put("checkNumber", LegacyParameters.text(payment.checkNumber()));
        fields.put("paymentStatus", LegacyParameters.text(payment.status()));
        return ResponseEntity.ok(PaymentResponse.of("issue", fields));
    }

    @RequestMapping("/payment/history")
    public ResponseEntity<PaymentResponse> history(HttpServletRequest request) {
        if (!signedIn(request.getParameter("user"))) {
            return redirectToLogin();
        }
        int claim = claimId(request);
        Map<String, String> fields = new LinkedHashMap<>();
        for (StoredPayment payment : payments.findByClaim(claim)) {
            int id = payment.paymentId();
            fields.put("checkNumber_" + id, LegacyParameters.text(payment.checkNumber()));
            fields.put("paymentAmount_" + id, LegacyParameters.money(payment.amount()));
            fields.put("paymentStatus_" + id, LegacyParameters.text(payment.status()));
        }
        String detail = "detail.do?paymentId=" + LegacyParameters.DEFAULT_PAYMENT_ID;
        String remittance = "remittance.do?claimId=" + claim;
        return ResponseEntity.ok(new PaymentResponse("history", fields, List.of(),
                List.of(detail, remittance, detail, remittance), Map.of()));
    }

    @RequestMapping("/payment/detail")
    public ResponseEntity<PaymentResponse> detail(HttpServletRequest request) {
        if (!signedIn(request.getParameter("user"))) {
            return redirectToLogin();
        }
        int id = LegacyParameters.integer(request.getParameter("paymentId"),
                LegacyParameters.DEFAULT_PAYMENT_ID);
        StoredPayment payment = payments.findById(id)
                .orElseThrow(() -> new MissingPaymentDataException("no payment " + id));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("detailPaymentId", LegacyParameters.integer(payment.paymentId()));
        fields.put("detailPaymentClaim", LegacyParameters.integer(payment.claimId()));
        fields.put("detailPaymentAmount", LegacyParameters.money(payment.amount()));
        fields.put("detailPaymentCheck", LegacyParameters.text(payment.checkNumber()));
        fields.put("detailPaymentDate", LegacyParameters.text(payment.issuedDate()));
        fields.put("detailPaymentStatus", LegacyParameters.text(payment.status()));
        Map<String, String> display = new LinkedHashMap<>();
        display.put("payeeName", LegacyParameters.text(payment.payeeName()));
        display.put("paymentMethod", LegacyParameters.text(payment.paymentMethod()));
        return ResponseEntity.ok(new PaymentResponse("detail", fields, List.of(),
                List.of(), display));
    }

    @RequestMapping("/payment/remittance")
    public ResponseEntity<PaymentResponse> remittance(HttpServletRequest request) {
        if (!signedIn(request.getParameter("user"))) {
            return redirectToLogin();
        }
        int claim = claimId(request);
        List<StoredPayment> rows = payments.findByClaim(claim);
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (StoredPayment payment : rows) {
            int id = payment.paymentId();
            fields.put("remitCheck_" + id, LegacyParameters.text(payment.checkNumber()));
            fields.put("remitDate_" + id, LegacyParameters.text(payment.issuedDate()));
            fields.put("remitAmount_" + id, LegacyParameters.money(payment.amount()));
            fields.put("remitStatus_" + id, LegacyParameters.text(payment.status()));
            display.put("payeeName_" + id, LegacyParameters.text(payment.payeeName()));
        }
        fields.put("remitClaimId", LegacyParameters.integer(claim));
        fields.put("remitCount", LegacyParameters.integer(rows.size()));
        fields.put("remitTotal", LegacyParameters.money(payments.totalIssued(claim)));
        return ResponseEntity.ok(new PaymentResponse("remittance", fields, List.of(),
                List.of(), display));
    }

    private static int claimId(HttpServletRequest request) {
        return LegacyParameters.integer(request.getParameter("claimId"),
                LegacyParameters.DEFAULT_CLAIM_ID);
    }

    private static boolean signedIn(String user) {
        return user != null && !user.isEmpty();
    }

    private static ResponseEntity<PaymentResponse> redirectToLogin() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(LOGIN)).build();
    }
}
