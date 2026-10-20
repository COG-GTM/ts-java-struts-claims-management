# CHG-002 — A payment above the newest settlement amount is rejected

Status: Approved
Date: 2026-10-20
Applies to: `services/payment-service` only. The Struts monolith under
`src/main` is not changed and keeps accepting the payment.
Rules affected: PAY-R15 (v1 superseded by v2); PAY-R07, PAY-R08, PAY-R13 and
PAY-R14 (unchanged, but no longer reached on this path)

## Why

The legacy issue action has no business checks. It reads the newest
settlement of the claim only to take its id and, when `amount` is missing or
not a number, its amount
(`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23`,
`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31`). Any
amount the operator types is written, however far above the settlement it
is: 999999 for claim 1, settled at 0.00, is accepted twice
(`live:issue-no-business-checks#2`, `live:issue-no-business-checks#3`). A
cheque for more than the agreed settlement can leave the desk with no warning.

## Before (legacy, and the service up to this change)

Precondition `parity/preconditions/payment.sql#chg-002-above-settlement`:
fresh seed plus settlement 121 for claim 119 with a settlement amount of 1000.

`POST /claims/payment/issue.do` as `supervisor` with `claimId=119`,
`payeeName=Over Cap Claimant`, `amount=1000.01`, `paymentMethod=CHECK`:

* legacy: status 200, `issue.jsp`, fields `paymentAmount` 1000.01,
  `checkNumber` CHK-61, `paymentStatus` ISSUED; PAYMENT row 61 written with
  amount 1000.01 and settlement 121. Observed live against the legacy app on
  8080 by `make payment-spec-proof`, case `chg-002-above-settlement`
  (`docs/specs/SPEC-PAY-001-proof.json`). No transcript covers this request:
  the scenario is declared with its own request block in
  `parity/routes.json`, `payment_scenarios.chg-002-above-settlement`.
* service before this change: the same answer and the same row (PAY-R15 (v1)).

## After (service only)

`POST` or `GET /payment/issue` checks the amount after the claim fallback and
the newest-settlement lookup, and before the payment id is read:

* the payment amount is the parsed `amount`, or the settlement amount when
  `amount` is missing or not parseable — unchanged (PAY-R07, PAY-R08);
* when it is greater than the newest settlement's `settlement_amount`, the
  answer is status 200, screen `issue`, `fields`, `display` and `links` empty,
  and `errors` exactly `["payment.amount.aboveSettlement"]`. No PAYMENT row is
  written and no payment id is used;
* an amount equal to the settlement amount is accepted; `NaN` is never
  greater, so it is accepted; a negative amount is accepted;
* a claim with no settlement still ends on the error screen (PAY-R06), because
  the lookup comes first.

The same request as above now answers the validation key and writes nothing.

Unchanged by this record: the claim fallback to 119, the amount fallback, the
newest-settlement choice, the next id (max + 1 over the whole table), the
check number `CHK-<id>`, the issued date 2019-04-03, the status `ISSUED`, the
GET write, the operator check and every read screen.

## Rules affected

| Rule | Effect |
| --- | --- |
| PAY-R15 (v1) | Legacy text kept, marked superseded for the service; still the behaviour of the monolith |
| PAY-R15 (v2) | New text: an amount above the newest settlement amount is a validation answer with key `payment.amount.aboveSettlement`, no row, no id used |
| PAY-R07 (v1), PAY-R08 (v1) | Unchanged — the amount is parsed and falls back as before; the cap compares that value |
| PAY-R13 (v1), PAY-R14 (v1) | Unchanged, but an over-cap request with a missing or over-long payee or method now gets the validation answer before the insert can fail |

## Tests

* `PaymentIssueEndpointTest.amountAboveNewestSettlementIsRejectedAndWritesNothing`
  (CHG-002) — 1000.01 against settlement 121 of 1000 is rejected with the one
  key and no row; 1000.00 afterwards is accepted as CHK-61.
* `PaymentIssueEndpointTest.capIsTheNewestSettlementAmount` (CHG-002) — an
  older, larger settlement does not raise the cap.
* `PaymentIssueEndpointTest.capComparesTheParsedAmount` (CHG-002) — 1.005 and
  1e3 are rejected on a 0.00 settlement; -50, NaN and an unparsable amount are
  still written.
* `PaymentIssueEndpointTest.capRunsAfterTheSettlementLookupAndBeforeTheInsert`
  (CHG-002) — a missing payee over the cap gets the validation answer; an
  unknown claim is still the error screen.
* `PaymentIssueEndpointTest.rawTextAndNoOtherBusinessChecks` — was
  `rawTextAndNoBusinessChecks` (PAY-R15 (v1)); now proves that within the cap
  a closed claim, a paid claim and a repeated request are still accepted.
* The other issue tests that send a positive amount to claims 1, 119 or 120
  (settled at 0.00 in the fixture) first raise that settlement amount; the
  request and the rule each proves are unchanged.

## Parity effect

The new scenario `chg-002-above-settlement` is reported CHANGED (CHG-002):
the service answer equals the approved answer in
`parity/routes.json` `approved_differences`, not the legacy answer.

On the fresh seed the newest settlement of claims 1, 119 and 120 is 0.00
(`src/main/resources/db/seed.sql:495`, `src/main/resources/db/seed.sql:613-614`),
so twelve existing scenarios that pay a positive amount to those claims are
now judged against this record and reported CHANGED (CHG-002). Their
preconditions are kept as they were. Each approval lists the steps and
database rows that differ; every other step is still compared with the legacy
answer, and `tools/parity/payment_parity.py` fails a step marked as the cap
rejection unless the amount really is above the newest settlement amount in
the service database just before the request. The list of scenarios is in
the generated block below.

## Impact of CHG-002

<!-- impact:begin -->

Generated by `make changes` (tools/changes.py) from the specs, `parity/routes.json`, `parity/report.json`, the test javadocs and the git history of commits naming CHG-002. Do not edit by hand.

### Rule versions touched

| Rule version | Spec | Effect |
| --- | --- | --- |
| PAY-R15 (v1) | `docs/specs/SPEC-PAY-001.md` | superseded by v2 |
| PAY-R15 (v2) | `docs/specs/SPEC-PAY-001.md` | added (CHG-002) |

### Scenarios added (1)

| Scenario | Verdict | What the approval says |
| --- | --- | --- |
| `chg-002-above-settlement` | CHANGED (CHG-002) | 1000.01 for claim 119, one cent above its newest settlement 121 of 1000.00, is rejected with payment.amount.aboveSettlement and writes nothing; the legacy app writes payment 61 |

### Scenarios reclassified (12)

| Scenario | Verdict | What the approval says |
| --- | --- | --- |
| `payment_issue` | CHANGED (CHG-002) | 1000.00 for claim 119, whose newest settlement is 0.00 under the declared fresh-seed precondition, is rejected with payment.amount.aboveSettlement; payment 61 is not written |
| `issue-post` | CHANGED (CHG-002) | 1000.00 for claim 119 (settlement 0.00) is rejected and payment 61 is not written, so the follow-up detail of payment 61 is the error screen |
| `issue-get` | CHANGED (CHG-002) | The GET of 10 for claim 1 (settlement 0.00) is rejected; nothing is written |
| `issue-claim-fallback` | CHANGED (CHG-002) | 5, 6 and 7 for the fallback claim 119 (settlement 0.00) are rejected; nothing is written |
| `issue-amount-raw` | CHANGED (CHG-002) | 1.005 and 1e3 for claim 119 (settlement 0.00) are rejected; -50 and NaN are not above it and are still written, as payments 61 and 62, so the remittances list only those |
| `issue-check-number` | CHANGED (CHG-002) | 1000.00 for claim 119 (settlement 0.00) is rejected before any check number is made; nothing is written |
| `issue-next-id` | CHANGED (CHG-002) | 1000.00 for claim 119 (settlement 0.00) is rejected; id 501 is not used and only the gap row 500 remains |
| `issue-required-columns` | CHANGED (CHG-002) | The cap runs before the insert: a missing payee or method with 10, and the complete 1000.00 request, for claim 119 (settlement 0.00) get the validation answer instead of the error screen or payment 61; nothing is written |
| `issue-column-limits` | CHANGED (CHG-002) | The cap runs before the insert: the over-long and the maximum-length payee and method with 1000.00 for claim 119 (settlement 0.00) get the validation answer instead of the error screen or payment 61; nothing is written |
| `issue-raw-text` | CHANGED (CHG-002) | 1000.00 for claim 119 (settlement 0.00) is rejected; payment 61 does not exist, so its detail is the error screen and the remittance of claim 119 is empty |
| `issue-no-business-checks` | CHANGED (CHG-002) | 1000.00 for CLOSED claim 120 and 999999 twice for claim 1 (both settled at 0.00) are rejected; the history of claim 1 shows only payment 1 |
| `adjuster-issue` | CHANGED (CHG-002) | adjuster7's 1000.00 for claim 119 (settlement 0.00) is rejected, with adjuster7 still the operator; nothing is written |

### Tests added (5)

| Test | Rules cited | File |
| --- | --- | --- |
| `PaymentIssueEndpointTest.rawTextAndNoOtherBusinessChecks` | PAY-R12 (v1), PAY-R15 (v2) | `services/payment-service/src/test/java/com/northstar/payment/web/PaymentIssueEndpointTest.java` |
| `PaymentIssueEndpointTest.amountAboveNewestSettlementIsRejectedAndWritesNothing` | PAY-R15 (v2) | `services/payment-service/src/test/java/com/northstar/payment/web/PaymentIssueEndpointTest.java` |
| `PaymentIssueEndpointTest.capIsTheNewestSettlementAmount` | PAY-R15 (v2) | `services/payment-service/src/test/java/com/northstar/payment/web/PaymentIssueEndpointTest.java` |
| `PaymentIssueEndpointTest.capComparesTheParsedAmount` | PAY-R07 (v1), PAY-R08 (v1), PAY-R15 (v2) | `services/payment-service/src/test/java/com/northstar/payment/web/PaymentIssueEndpointTest.java` |
| `PaymentIssueEndpointTest.capRunsAfterTheSettlementLookupAndBeforeTheInsert` | PAY-R06 (v1), PAY-R13 (v1), PAY-R15 (v2) | `services/payment-service/src/test/java/com/northstar/payment/web/PaymentIssueEndpointTest.java` |

### Files changed (23)

- `.github/workflows/ci.yml`
- `Makefile`
- `docs/TRACEABILITY.md`
- `docs/changes/CHG-001-invalid-deductible.html`
- `docs/changes/CHG-002-payment-amount-cap.html`
- `docs/changes/CHG-002-payment-amount-cap.md`
- `docs/specs/SPEC-PAY-001-proof.json`
- `docs/specs/SPEC-PAY-001.html`
- `docs/specs/SPEC-PAY-001.md`
- `parity/preconditions/payment.sql`
- `parity/report.html`
- `parity/report.json`
- `parity/report.md`
- `parity/routes.json`
- `services/payment-service/src/main/java/com/northstar/payment/web/PaymentController.java`
- `services/payment-service/src/test/java/com/northstar/payment/web/PaymentIssueEndpointTest.java`
- `services/payment-service/src/test/java/com/northstar/payment/web/PaymentReadEndpointTest.java`
- `tools/changes.py`
- `tools/parity/payment_parity.py`
- `tools/parity/report_template.html`
- `tools/spec/payment_proof.py`
- `tools/spec/spec_html.py`
- `tools/traceability.py`

<!-- impact:end -->
