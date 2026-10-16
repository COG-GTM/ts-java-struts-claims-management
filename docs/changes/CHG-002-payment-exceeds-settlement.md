# CHG-002 — A payment amount above the newest settlement amount is a validation error

Status: Approved
Date: 2026-10-16
Applies to: `services/payment-service` only. The Struts monolith under
`src/main` is not changed and keeps issuing the payment.
Rules affected: PAY-R07 (v1 superseded by v2), PAY-R05, PAY-R06, PAY-R08,
PAY-R14, PAY-R16 (unchanged; see below)

## Why

`PaymentIssueAction` reads `amount` with the lenient `decimal` coercion of
`ClaimsActionSupport` and writes it without comparing it to anything
(`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31`,
`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45`): the
only check between the request and the `INSERT` is the table schema (PAY-R12).
An operator can therefore pay out more than the claim was settled for. The
extracted service is required to refuse an amount above the claim's newest
`settlement_amount`, the same `SETTLEMENT` row PAY-R05 already selects for the
payment's `settlement_id` and for the amount fallback.

## Before (legacy, and the service up to this change)

Observed on the running legacy app (Jetty 8080, fresh `make seed`, logged in as
`supervisor`) on 2026-10-16, with curl against the real `.do` endpoints because
`payment/issue.jsp` is a confirmation page, not an input form:

1. `POST /claims/settlement/save.do` with `claimId=119`,
   `coveredAmount=5000.00`, `deductible=500.00`, `depreciation=0.00` — status
   200, `forward:/WEB-INF/jsp/settlement/save.jsp`, `settlementAmount`
   `1000.00`, `savedBy` `supervisor`. This is the same request the capture
   flow makes before `payment_issue` (transcript `settlement_save`), so claim
   119's newest settlement is now id 121 at 1000.00 instead of the seeded
   settlement 119 at 0.00.
2. `POST /claims/payment/issue.do` with `claimId=119`,
   `payeeName=Reserved Claimant`, `amount=1500.00`, `paymentMethod=CHECK` —
   status 200, `forward:/WEB-INF/jsp/payment/issue.jsp`, `paymentAmount`
   `1500.00`, `checkNumber` `CHK-61`, `paymentStatus` `ISSUED`.
3. `GET /claims/payment/history.do?claimId=119` — `checkNumber_61` `CHK-61`,
   `paymentAmount_61` `1500.00`, `paymentStatus_61` `ISSUED`;
   `GET /claims/payment/remittance.do?claimId=119` — `remitCount` `1`,
   `remitTotal` `1500.00`.

The legacy app pays 1500.00 against a 1000.00 settlement, and so did the
service before this change. The same thing happens at a smaller scale in the
recorded live run: step L16 (`docs/specs/SPEC-PAY-001-live.json`) issues
12.345 to claim 119 while its newest settlement is the seeded 0.00.

## After (service only)

`POST` or `GET /payment/issue` resolves the claim (PAY-R04) and its newest
settlement (PAY-R05), parses `amount` exactly as before (PAY-R06, PAY-R07 v1,
PAY-R08), and then:

* if the parsed amount is strictly greater than the settlement's
  `settlement_amount` — the response is status 200, screen `issue`, `fields`
  empty, and `errors` exactly `["payment.amount.exceedsSettlement"]`; no
  `PAYMENT` row is written and `max(payment_id) + 1` is not read, so the next
  payment keeps the id the rejected request would have taken;
* otherwise — unchanged: the payment is issued exactly as PAY-R14 to PAY-R17
  describe.

So an amount equal to the settlement amount is still paid; a missing or
unparseable amount still falls back to the settlement amount and is paid
(PAY-R06); `-5`, `1e3` and ` 5 ` are still paid when they do not exceed the
settlement (PAY-R07 v2 keeps the parsing of v1); `NaN` is still paid, because
`NaN > x` is false (PAY-R08); a claim with no settlement still ends on the
error screen (PAY-R05). The check is in
`services/payment-service/src/main/java/com/northstar/payment/web/PaymentController.java`
(`issue`, before `payments.nextId()`).

## Rules affected

* PAY-R07 (v1) is superseded by PAY-R07 (v2) in `docs/specs/SPEC-PAY-001.md`
  version 0.2; the v1 text is retained as the legacy behaviour the monolith
  keeps.
* PAY-R05, PAY-R06, PAY-R08, PAY-R14 and PAY-R16 are unchanged; v2 only adds
  a comparison between the already-parsed amount and the already-selected
  settlement, ahead of the id read and the insert.
* OQ-05 (`docs/specs/OPEN-QUESTIONS.md`) is partly answered: of the inputs it
  lists, only an amount above the settlement is now rejected.
* `docs/KNOWN_LEGACY_QUIRKS.md` notes that the monolith keeps accepting it.

## Tests

* `PaymentIssueEndpointTest.amountAboveTheNewestSettlementIsRejectedWithoutConsumingAnId`
  (`services/payment-service/src/test/java/com/northstar/payment/web/PaymentIssueEndpointTest.java`):
  with claim 119's newest settlement at 1000.00, `1500.00` and `1000.01` are
  rejected with status 200, screen `issue`, no fields and the single error key;
  the payment count and `max(payment_id)` do not move; `1000.00`, an omitted
  amount and `NaN` are then issued, the first with the id the rejected requests
  did not consume.
* The existing issue and history tests now start from the capture's state
  (settlement 121 for claim 119 at 1000.00, `capture-state.sql`) instead of
  the bare seed, because the 1000.00 they send would otherwise be above the
  seeded 0.00 settlement; their assertions are otherwise unchanged except that
  payment 61 carries `settlement_id` 121 as the transcript recorded.

## Parity effect

* New scenario `payment_issue_over_settlement` in `parity/routes.json`,
  defined with its own request block (step 2 above) and its own database
  precondition `parity/preconditions/payment/chg-002-settlement-1000.sql`
  (settlement 121 for claim 119 at 1000.00, the state step 1 produced). Its
  legacy expected block is the response recorded above; the approved block is
  status 200, screen `issue`, no business fields, `payment.amount.exceedsSettlement`,
  no payment 61 and `payment.count.claim.119` 0. Verdict: CHANGED (CHG-002).
* Existing live-run scenario `payment_issue_claim_fallback_unrounded` (step
  L16, 12.345 to claim 119 at the seeded 0.00 settlement) is the same
  behaviour at a different amount, so it is also judged against an approved
  block (status 200, screen `issue`, the single error, no payment 63) and
  reported CHANGED (CHG-002). The rules it was evidence for, PAY-R04 and
  PAY-R09, remain proved by
  `PaymentIssueEndpointTest.unparseableClaimIdIs119AndAmountIsStoredUnrounded`
  and by the live run's db row 63 cited in the spec.
* Every other payment scenario sends an amount at or below the claim's newest
  settlement (claim 118 at 1118.00, or claim 119 under `capture-state.sql` at
  1000.00) and stays PASS. Settlement scenarios are not affected.
