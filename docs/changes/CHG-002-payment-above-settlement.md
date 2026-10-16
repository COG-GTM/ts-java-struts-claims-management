# CHG-002 — A payment above the latest saved settlement is rejected

Status: Approved
Date: 2026-10-16
Applies to: `services/payment-service` only. The Struts monolith under
`src/main` is not changed and keeps issuing these payments.
Rules affected: PAY-R05 (v1 superseded by v2), PAY-R03 (unchanged),
PAY-R04 (unchanged), PAY-R08 (no longer holds for the service on this path)

## Why

The legacy issue action takes `amount` as typed and never compares it with the
settlement it pays against
(`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,30-31`).
Any amount is issued, including one larger than the settlement the adjuster
saved. The extracted service is required to refuse such a payment.

## Before (legacy, and the service up to this change)

Starting from a fresh `make seed`, after transcript `settlement_save` has
written settlement 121 for claim 119 at 1000.00, `POST /claims/payment/issue.do`
with `claimId=119`, `payeeName=Reserved Claimant`, `amount=1500.00`,
`paymentMethod=CHECK`:

* legacy: status 200, `forward:/WEB-INF/jsp/payment/issue.jsp`,
  `paymentAmount` 1500.00, `checkNumber` CHK-61, `paymentStatus` ISSUED, and
  PAYMENT 61 written for claim 119 against settlement 121 with amount 1500.0
  (`parity/legacy/payment_issue_above_settlement.json`, written by
  `make payment-legacy-observe`);
* service before this change: the same issue screen and the same row.

## After (service only)

`POST /payment/issue` looks up the claim's latest settlement (PAY-R03), parses
`amount` with the same fallback (PAY-R05), and then:

* the parsed amount is greater than that settlement's `settlement_amount` — the
  response is status 200, screen `issue`, `fields` empty and `errors` exactly
  `["payment.amount.exceedsSettlement"]`; no PAYMENT row is written and the
  next `payment_id` is not consumed;
* otherwise — unchanged, the payment is issued.

Details:

* "Greater" compares the parsed double, unrounded: `1000.001` is above
  `1000.00` and is rejected, although it would print as `1000.00`.
* An amount equal to the settlement is issued. The omitted or unparseable
  amount falls back to the settlement amount, so it is never rejected.
* The check runs after the settlement lookup. A claim with no settlement still
  reaches the error screen (PAY-R04); there is nothing to compare against.
* The check runs before the insert, so a request that is both above the
  settlement and missing `payeeName` or `paymentMethod` gets the validation
  error, not the error screen of PAY-R07.
* `NaN` is not greater than any number, so it is still issued as in the
  legacy app.

The answer is the issue screen with a validation error, as CHG-001 does for an
invalid deductible: the request is an input mistake, not a system failure.

## Rules affected

| Rule | Effect |
| --- | --- |
| PAY-R05 (v1) | Legacy text kept, marked superseded for the service |
| PAY-R05 (v2) | New text: an amount above the latest settlement gives status 200, the issue screen, no business fields, one validation error `payment.amount.exceedsSettlement`, and no row written |
| PAY-R03 (v1) | Unchanged — the latest settlement is still the highest `settlement_id` |
| PAY-R04 (v1) | Unchanged — a claim without a settlement still fails the request |
| PAY-R08 (v1) | Still describes the legacy (`validate="false"`, never any errors); the service now emits one validation key on this one path |

## Tests

* `PaymentIssueEndpointTest.issueAboveTheLatestSettlementIsAValidationErrorAndWritesNothing`
  — 1500.00 against settlement 121 at 1000.00: 200, screen `issue`, empty
  `fields`, the one key, row count and next id unchanged.
* `PaymentIssueEndpointTest.anyFractionAboveTheSettlementIsRejected` —
  1000.001 is rejected.
* `PaymentIssueEndpointTest.amountsUpToTheSettlementAreIssued` — claim 118
  (settlement 1118.00): 500 and 1118.00 are issued, 1118.01 is rejected.
* `PaymentIssueEndpointTest.claimWithoutSettlementStillFailsBeforeTheCheck` —
  claim 9999 is still the error screen.
* `PaymentIssueEndpointTest.issueDefaultsClaimAndAmountFromTheLatestSettlement`
  — the omitted amount falls back to the latest settlement and is issued.
* The tests that issue 1000.00 for claim 119 (issue, history, detail,
  remittance) now insert settlement 121 at 1000.00 first, the state the
  transcripts were captured in. Against the bare seed, settlement 119 is 0.00
  and the payment would be above it.

## Parity effect

The seed gives claim 119 a settlement of 0.00, but the payment transcripts were
captured after `settlement_save` saved 1000.00. Under this change that
difference matters, so payment scenarios now state their database:

* `payment_issue` runs on `parity/preconditions/payment/capture-state.sql`
  (seed plus settlement 121 at 1000.00). Its 1000.00 equals the settlement, so
  it stays `PASS`; `payment_history` follows it and stays `PASS`.
* `payment_issue_above_settlement` is a new scenario with no transcript. It
  runs on its own precondition,
  `parity/preconditions/payment/chg-002-above-settlement.sql`, sends 1500.00
  for claim 119 and is judged against the response approved above, so
  `make parity` reports it as `CHANGED (CHG-002)` and fails on anything else.
  The legacy side of the comparison is in
  `parity/legacy/payment_issue_above_settlement.json`.

The transcripts are untouched: `make capture` followed by
`git diff --exit-code transcripts/` is clean.
