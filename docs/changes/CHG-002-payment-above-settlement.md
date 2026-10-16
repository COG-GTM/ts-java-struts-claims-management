# CHG-002 — A payment amount above the latest settlement is a validation error

Status: Approved
Date: 2026-10-16
Applies to: `services/payment-service` only. The Struts monolith under
`src/main` is not changed.
Rules affected: PAY-R05 (v1 superseded by v2), PAY-R04 (unchanged),
PAY-R06 (unchanged)

## Why

`PaymentIssueAction` reads the `amount` parameter with the double fallback and
hands it straight to the DAO
(`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:28-31`,
`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45`). The
latest settlement is looked up only to fill `settlement_id`
(`src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-110`); its
`settlement_amount` is never compared with the payment. The mapping has
`validate="false"` (`src/main/webapp/WEB-INF/struts-config.xml:223-231`), so a
cheque for any amount is issued: live L10 of `docs/specs/SPEC-PAY-001-live.json`
pays 5000.00 against a 1000.00 settlement and gets `CHK-66`.

The extracted service is required to refuse an amount above the claim's
latest saved settlement. The monolith keeps accepting it; that is recorded in
`docs/KNOWN_LEGACY_QUIRKS.md`.

## Before (legacy, and the service up to this change)

`POST /claims/payment/issue.do` with `claimId=119`, `payeeName=Reserved
Claimant`, `amount=5000.00`, `paymentMethod=CHECK`, while the latest
settlement of claim 119 is settlement 121 at 1000.00:

* status 200, forward `/WEB-INF/jsp/payment/issue.jsp`;
* fields `paymentAmount=5000.00`, `checkNumber=CHK-<next id>`,
  `paymentStatus=ISSUED`; no validation errors;
* a PAYMENT row with amount `5000.0E0` and `max(payment_id) + 1` as its id.

Observed: live L10 (`CHK-66`, payments 61 to 65 already present), and the
same request on the legacy app after `make seed` plus
`parity/preconditions/payment/chg-002-settlement-1000.sql` (settlement 121
only, no payment above 60), which answers `CHK-61`. The latter is the
`legacy` block of the scenario `payment_above_settlement_rejected` in
`parity/routes.json`; no transcript covers it, so the request and response
are quoted there and shown in the recording attached to the pull request.
The same holds for a positive amount against a settlement at 0: live L21,
L22, L23 and L36 issue 5.00, 20.00, 1.00 and 2.00 against settlements 1 and
120 (`CHK-73` to `CHK-76`).

## After (service only)

The same request against `services/payment-service`:

* status 200, screen `issue`;
* `fields` empty; `errors` = `["payment.amount.aboveSettlement"]`;
* no PAYMENT row written and no id consumed: the next accepted request still
  takes `max(payment_id) + 1` (PAY-R06).

The comparison is `amount > settlement_amount` on the parsed double, after
the PAY-R04 fallback, so:

* an amount equal to the settlement is accepted;
* a missing or unparseable amount still falls back to the settlement amount
  and is accepted (PAY-R04);
* `-250.00`, `1.005`, `1e3` (1000.00 against 1000.00) and `NaN` (not greater
  than any number) are still stored as sent and printed with `%.2f`
  (PAY-R05 v1 text, kept in v2);
* a claim whose latest settlement is 0 rejects every positive amount and still
  issues the fallback 0.00.

The key `payment.amount.aboveSettlement` follows the pattern of the one
existing validation key (`settlement.deductible.invalid`, CHG-001); the legacy
bundle has no such key because the legacy app never validates this field.

## Rules affected

| Rule | Effect |
| --- | --- |
| PAY-R05 (v1) | Legacy text kept, marked superseded for the service |
| PAY-R05 (v2) | New text: amount above the latest settlement_amount is status 200, issue screen, no business fields, one validation error `payment.amount.aboveSettlement`, no row written, no id used; everything else as v1 |
| PAY-R04 (v1) | Unchanged — missing or unparseable amount still means the settlement amount |
| PAY-R06 (v1) | Unchanged — a rejected request does not read `max(payment_id)` |

## Tests

* `PaymentIssueEndpointTest.amountAboveTheSettlementIsRejectedAndWritesNothing`
  (CHG-002, PAY-R05 v2) — `amount=5000.00` and `1000.01` against settlement
  121 at 1000.00 give 200, screen `issue`, empty `fields`,
  `errors` = `["payment.amount.aboveSettlement"]`, the row count stays 60 and
  the next accepted request is `CHK-61`.
* `PaymentIssueEndpointTest.zeroSettlementRejectsPositiveAmountsButIssuesTheFallback`
  (CHG-002) — claim 120 (settlement 120 at 0) rejects 1.00 over POST and 2.00
  over GET and still issues the fallback 0.00.
* `PaymentIssueEndpointTest.amountsNotAboveTheSettlementAreAcceptedAsLegacyDoes`
  — `-250.00`, `1.005`, `NaN`, `1e3` and the equal `1000.00` are accepted as
  live L08, L09, L11 and L12 show.
* `PaymentIssueEndpointTest.getIsMappedAndClosedClaimsAreAccepted` — PAY-R12
  unchanged; its claim 120 request now sends 0.00 because 12.00 is above that
  claim's settlement.
* `PaymentIssueEndpointTest.missingAmountMeansTheSettlementAmount` and the
  other issue tests — unchanged.

## Parity effect

Five live requests of `docs/specs/SPEC-PAY-001-live.json` no longer match the
service: `payment_live_L10` (5000.00 against 1000.00) and `payment_live_L21`,
`L22`, `L23`, `L36` (positive amounts against settlements at 0). Each is
registered in `parity/routes.json` against this record with the approved
service response and its unchanged `payment_rows_through`, so `make parity`
reports them as `CHANGED (CHG-002)` and still fails if the service answers
anything else.

The scenario `payment_above_settlement_rejected` is the explicit CHANGED
scenario of this change. It has its own precondition,
`parity/preconditions/payment/chg-002-settlement-1000.sql` (settlement 121 at
1000.00 on claim 119, no payment above 60), its own request block and the
legacy observation above; the service must answer the validation error and
leave the PAYMENT table at 60 rows. Every other payment scenario stays
`PASS`, and every settlement scenario is untouched.

The transcripts and the live proof are untouched: `transcripts/` is written
only by `make capture` and `docs/specs/SPEC-PAY-001-live.json` only by
`make payment-spec-proof`, both against the unchanged monolith, and
`make capture` followed by `git diff --exit-code transcripts/` is clean.
