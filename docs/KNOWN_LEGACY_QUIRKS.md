# Known legacy quirks

Legacy behaviour that the extracted services reproduce on purpose. Every entry
is a recorded choice: it stays until a `docs/changes/CHG-nnn` record approves a
change (ADR-001 and ADR-002, "Behaviour freeze"). Each quirk names the rule
that specifies it and where the legacy code does it; the rule carries the
transcript or live-run step that proved it.

## Settlement slice (services/settlement-service, ADR-001)

The settlement quirks (the 10000 policy-limit fallback of OQ-01, the literal
`calculated_date` 2019-04-01, the half-cent rounding) are listed under
"Consequences" in `docs/decisions/ADR-001-settlement-boundary.md`; they are
not repeated here so that this file adds no new mention of a settlement rule.

## Payment slice (services/payment-service, ADR-002)

| Quirk | Rule | Legacy code |
| --- | --- | --- |
| `GET /payment/issue` writes a payment exactly as a POST does; the mapping is not restricted to POST. | SPEC-PAY-001 PAY-R03 | `src/main/webapp/WEB-INF/struts-config.xml:223-231` |
| A missing or unparseable `claimId` is claim 119 (issue, history, remittance); a missing or unparseable `paymentId` is payment 61 (detail). | PAY-R04, PAY-R18, PAY-R22, PAY-R25 | `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37` |
| A missing or unparseable `amount` (including `1,000`) is paid at the newest settlement's amount, which is 0 for the seeded claims 119 and 120. | PAY-R06 | `PaymentIssueAction.java:30-31`, `ClaimsActionSupport.java:39-45` |
| Negative amounts, exponents (`1e3`), surrounding spaces and `NaN` are accepted and stored; NaN then makes the claim's remittance total NaN (OQ-05). | PAY-R07, PAY-R08, PAY-R27 | `ClaimsActionSupport.java:39-45`, `PaymentDAO.java:122-137` |
| The amount is stored unrounded (`12.345`); only the screens round to two decimals. | PAY-R09, PAY-R28 | `PaymentDAO.java:150-158`, `tag/FieldTag.java:52-58` |
| `payeeName` and `paymentMethod` are stored verbatim, including the empty string and lower-case methods; there is no allowed-method list (OQ-06). | PAY-R10 | `PaymentIssueAction.java:29,32` |
| There is no input validation; a `checkNumber` parameter is ignored (OQ-03). The only checks are the schema's NOT NULL and VARCHAR lengths, whose violation is the generic error page. | PAY-R11, PAY-R12, PAY-R29 | `struts-config.xml:29-32,223-231`, `src/main/resources/db/schema.sql:110,112` |
| `payment_id` is `max(payment_id)+1` read before the insert, with no sequence or lock (OQ-08); the check number is `CHK-<id>` without padding, unlike the seed's `CHK-00001`. | PAY-R13, PAY-R14 | `ClaimsActionSupport.java:61-75`, `PaymentIssueAction.java:33` |
| Every payment is issued with `issued_date` `2019-04-03` and `status` `ISSUED`, while the screens show the as-of date `2019-04-01` (OQ-04). | PAY-R15, PAY-R30 | `PaymentIssueAction.java:34-35`, `src/main/resources/northstar.properties:2` |
| The history screen's detail link is hard-coded to `paymentId=61` and the screen renders no count or total (OQ-07). Presentation only; not reproduced by the service. | PAY-R20, PAY-R21 | `src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19` |
| A detail request for a payment that does not exist is the generic error page, not a not-found screen (OQ-02). | PAY-R23 | `PaymentDetailAction.java:18-20`, `jsp/payment/detail.jsp:10` |
