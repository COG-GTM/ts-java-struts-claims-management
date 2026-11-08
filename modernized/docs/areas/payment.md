# Payment area

## Mapping

All four legacy actions map to request handlers in
`com.northstar.claims.web.payment.PaymentController`. URLs retain the
`/payment/{issue,history,detail,remittance}.do` paths under `/claims`.
Each forwards with HTTP 200 to its corresponding `templates/payment/*.html`,
emitting the original `/WEB-INF/jsp/payment/*.jsp` marker.
Both GET and POST remain accepted, including GET payment issuance.

Issue consumes claimId, payeeName, amount and paymentMethod directly as the
action did. PaymentForm's checkNumber is ignored; its inherited fields have
no effect. No form record is needed because no form property was read by
the action. All mappings have validate=false, PaymentForm/BaseForm return
empty errors, and validation.xml contains no payment rules. No validation
keys are introduced.

## Preserved quirks and bugs

* claimId defaults to 119; paymentId defaults to 61, including malformed input.
* IDs use the shared max(payment_id)+1 helper: the snapshot's next ID is 61,
  not a hard-coded ID. Its concurrency race is deliberately unchanged.
* Check numbers are CHK-<id>, ignoring submitted checkNumber; dates are fixed
  at 2019-04-03 and status at ISSUED.
* Missing/malformed amounts fall back to the latest settlement amount.
  Double parsing, JDBC storage rounding and shared field formatting remain
  unchanged; negative and over-settlement payments are not rejected.
* Missing settlement dereference and missing detail bean render the global
  HTTP-200 error page, rather than a new 404 or validation response.
* Payment history is ordered by payment_id and retains duplicate navigation
  links to fixed payment 61.
* Remittance's totalIssued sums all payment statuses, not only ISSUED.
* Null payee/method fail database NOT NULL constraints and forward to the
  HTTP-200 global error page; blank payee and repeat issuance remain allowed.

## Reuse and decisions

Shared PaymentDAO, SettlementDAO and ClaimsSupport supply all required SQL
and parsing; no inline SQL or area repository was necessary. Shared ns and
layout fragments retain escaping, dates, money, audit and resource keys.
No shared Wave 0 files, parity tools, snapshots or legacy files were edited.
Only ns fields from the JSPs are exposed as business-field spans.

## Verification

`make -C modernized build`, `make -C modernized replay AREA=payment`,
and `make -C modernized test` exercise the app. PaymentIntegrationTest uses
real in-memory HSQLDB and authenticated MockMvc requests across all four
views, persisted issuance, empty lists, invalid/default parameters and error
forwards. Golden replay uses the file-backed snapshot database and login.
Browser visual appearance is not covered by these parity checks.
