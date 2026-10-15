# CHG-002 — A payment above the settlement amount is a validation error

Status: Approved
Date: 2026-10-15
Applies to: `services/payment-service` only. The Struts monolith under
`src/main` is not changed.
Rules affected: PAY-R05 (v1 superseded by v2), PAY-R03 (unchanged, supplies the
cap), PAY-R04 (unchanged), PAY-R08 (no longer holds for the service on this
path), PAY-R09 (unchanged: no key is consumed on the rejected path)

## Why

The legacy issue action takes any parseable `amount` as sent
(`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31`,
`ClaimsActionSupport.java:39-45`) and inserts it; nothing compares it with the
settlement it is paying. A payment of 1500.00 against a settlement of 1118.00
is issued, numbered and marked ISSUED like any other, so an operator can pay
out more than the claim was settled for with no warning. The extracted service
is required to reject such an amount as input validation.

## Before (legacy, and the service up to this change)

`POST /claims/payment/issue.do` with `claimId=118`, `payeeName=Claimant 118`,
`amount=1500.00`, `paymentMethod=CHECK`, where claim 118's latest settlement is
118 with `settlement_amount` 1118.00 (`src/main/resources/db/seed.sql:612`):

* legacy: status 200, `forward:/WEB-INF/jsp/payment/issue.jsp`,
  `paymentAmount` 1500.00, `checkNumber` CHK-61, `paymentStatus` ISSUED; the
  row is then listed by `/claims/payment/history.do?claimId=118`. Confirmed
  against the running legacy app on 2026-10-15 (no transcript records this
  request; see "Parity effect");
* service: the same answer (`fields.paymentAmount` 1500.00, CHK-61, ISSUED)
  and a PAYMENT row for claim 118.

## After (service only)

`POST /payment/issue` resolves the claim and its latest settlement (PAY-R02,
PAY-R03) and parses the amount with the v1 fallback (PAY-R05 v1), then:

* the amount is strictly greater than the settlement's `settlement_amount` —
  the response is status 200, screen `issue`, `fields` empty, and `errors`
  exactly `["payment.amount.exceedsSettlement"]`. No PAYMENT row is written and
  the next `payment_id` is not consumed, because the check runs before the key
  allocation and the insert;
* the amount is equal to or below the settlement amount — unchanged.

A claim with no settlement still fails the request the legacy way (PAY-R04,
error screen, nothing written): there is nothing to compare against, so the
new key is not produced on that path. The fallback for a missing or
unparseable amount is unchanged and can never trip the cap (it *is* the
settlement amount).

Unchanged by this record: the parameter fallbacks (claimId 119), the verbatim
storage of `payeeName` and `paymentMethod`, the NOT NULL failures for a missing
payee or method (PAY-R07), the check number, issued date and status
(PAY-R10..R12), and the history, detail and remittance flows.

## Rules affected

| Rule | Effect |
| --- | --- |
| PAY-R05 (v1) | Legacy text kept unchanged, marked superseded for the service |
| PAY-R05 (v2) | New text: an amount above the latest settlement amount is status 200, issue screen, no business fields, one validation error `payment.amount.exceedsSettlement`, no PAYMENT row, no payment_id consumed |
| PAY-R03 (v1) | Unchanged — the latest settlement (highest `settlement_id`) supplies the cap |
| PAY-R04 (v1) | Unchanged — a claim with no settlement still fails the request |
| PAY-R08 (v1) | Still describes the legacy (`validate="false"`, always empty errors); the service now emits one validation key on this one path |
| PAY-R09 (v1) | Unchanged — the rejected path allocates no key |

## Tests

* `PaymentIssueEndpointTest.overSettlementAmountIsAValidationErrorAndWritesNothing`
  (CHG-002) — 1500.00 against claim 118 (settlement 1118.00) gives 200, screen
  `issue`, empty `fields`, `errors` = `["payment.amount.exceedsSettlement"]`,
  PAYMENT count and next `payment_id` unchanged.
* `PaymentIssueEndpointTest.amountEqualToTheSettlementIsStillIssued` (CHG-002)
  — exactly 1118.00 against claim 118 is still issued (strict comparison).
* `PaymentIssueEndpointTest.capDoesNotReplaceTheMissingSettlementFailure`
  (CHG-002) — 1500.00 against claim 9999 still reaches the error screen with
  no validation key (PAY-R04).
* `PaymentIssueEndpointTest.issueInsertsPayment61ForClaim119`,
  `issueDefaultsClaimAndAmountFromTheLatestSettlement`,
  `issueStoresNameAndMethodVerbatimAndLeavesClaimAlone` — unchanged
  behaviour: 1000.00 against the capture's 1000.00 settlement, the fallback
  amount, and an unparseable amount all still issue.
* `PaymentParametersTest.amountFallsBackToTheSettlementAmount` — unchanged;
  the parsing itself is not touched, the check sits after it.

## Parity effect

No existing payment transcript exercises an over-settlement amount:
`payment_issue` sends 1000.00 for claim 119 at the moment the capture's
`settlement_save` had just saved a 1000.00 settlement for that claim, and
`payment_history` only reads it back. `transcripts/` is written only by
`make capture` against the unchanged monolith, so no transcript of the new
behaviour can exist and none was hand-written.

The approved difference is evidenced instead by a scenario of its own,
`payment_issue_over_settlement`, registered in `parity/routes.json` with the
request above and the approved `expected` block (status 200, screen `issue`,
no business fields, `["payment.amount.exceedsSettlement"]`,
`payment.count.claim.118` 0). `tools/parity/payment_parity.py` replays it
from that request after the transcripts and reports `CHANGED (CHG-002)`, and
still fails if the service answers anything other than the approved response.
The legacy side of the difference is the "Before" section above (the live
legacy run on 2026-10-15), which a reviewer can repeat against `make run`.

Two consequences for the replay, both outside `transcripts/`:

* `payment_issue` can only stay `PASS` if the service holds the state the
  legacy capture held: the capture runs every scenario against one database,
  so when `payment_issue` was recorded, claim 119's latest settlement was the
  1000.00 one that `settlement_save` had written, not the seed's 0.00 one. The
  seed alone now trips the cap (1000.00 > 0.00) for a reason unrelated to the
  change. `parity/preconditions/payment.sql` restores that settlement;
  `make payment-run-parity` loads it (Spring profile `parity`) and the endpoint
  tests that mirror transcripts load it too. `make payment-run` and the seed
  are untouched.
* ADR-002's "Behaviour freeze" said the payment slice admits no approved
  differences and `payment_parity.py` enforced that; this record is the first
  one, so the script now judges approved payment differences the way the
  settlement one does.

Every other payment scenario (`payment_issue`, `payment_history`) and every
settlement scenario keeps its verdict. `make capture` followed by
`git diff --exit-code transcripts/` is clean.
