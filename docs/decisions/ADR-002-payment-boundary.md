# ADR-002: Payment slice as the second extracted service

Status: Accepted
Date: 2026-10-15

## Context

ADR-001 moved the settlement slice out of the Struts 1.3.10 / Java 7 monolith
into `services/settlement-service` (port 8083) and established how an
extraction is specified (`docs/specs/SPEC-*.md`), judged (`make parity`) and
traced (`make traceability`). The payment module is the natural next slice: it
is the only consumer of a saved settlement, it is covered by two recorded
transcripts (`payment_issue`, `payment_history`), and its rules are written up
in `docs/specs/SPEC-PAY-001.md` (29 rules, PAY-R01 to PAY-R29).

## Decision

Extract the payment slice — `/payment/issue`, `/payment/history`,
`/payment/detail`, `/payment/remittance` — into a single Spring Boot 3.5 /
Java 21 service at `services/payment-service`, listening on port 8084, built
the same way as the settlement service.

### Why this slice next

* It is covered by two of the 22 transcripts, so parity has a recorded
  definition for the write path (issue) and the read path (history).
* It writes to exactly one table, `PAYMENT`, and only reads `SETTLEMENT`, so the
  ownership boundary is clean.
* It reuses the settlement service's recipe (in-memory HSQLDB from the legacy
  scripts, `JdbcTemplate`, JSON with the `<ns:field>` names), so the second
  extraction tests that the recipe is repeatable rather than bespoke.

### Endpoints

| Legacy path | Service endpoint | Writes |
| --- | --- | --- |
| `POST /claims/payment/issue.do` | `POST /payment/issue` (`claimId`, `payeeName`, `amount`, `paymentMethod`) | `PAYMENT` |
| `GET /claims/payment/history.do` | `GET /payment/history` (`claimId`) | nothing |
| `GET /claims/payment/detail.do` | `GET /payment/detail` (`paymentId`) | nothing |
| `GET /claims/payment/remittance.do` | `GET /payment/remittance` (`claimId`) | nothing |

Every response has the same shape as the settlement service: `screen` (the
legacy forward: `issue`, `history`, `detail`, `remittance`, or `error`),
`fields` (the `<ns:field>` values, already formatted as the tag would print
them) and `errors` (always empty: PAY-R08).

### How each Struts piece maps

| Struts today | Payment service |
| --- | --- |
| `PaymentIssueAction`, `PaymentHistoryAction`, `PaymentDetailAction`, `PaymentRemittanceAction` (`extends ClaimsActionSupport`) | One `@RestController` with four endpoints |
| `PaymentForm` + raw `request.getParameter` reads | Request parameters read as strings with the same lenient coercion (`integer`, `decimal` of `ClaimsActionSupport`), kept in `LegacyParameters`; the form bean's `checkNumber` is ignored as it is today |
| `issue.jsp`, `history.jsp`, `detail.jsp`, `remittance.jsp` with `<ns:field>` | JSON `fields` with the same names: `paymentAmount`, `checkNumber`, `paymentStatus`; `checkNumber_<id>`, `paymentAmount_<id>`, `paymentStatus_<id>`; `detailPaymentId`, `detailPaymentClaim`, `detailPaymentAmount`, `detailPaymentCheck`, `detailPaymentDate`, `detailPaymentStatus`; `remitCheck_<id>`, `remitDate_<id>`, `remitAmount_<id>`, `remitStatus_<id>`, `remitClaimId`, `remitCount`, `remitTotal` |
| `FieldTag` `money` / `integer` / `date` formatting | `FieldFormatter` applying the same `String.format("%.2f")`, integer and `yyyy-MM-dd` rules (PAY-R27, PAY-R28) |
| `PaymentDAO`, `SettlementDAO.findByClaim` + `ConnectionPool` + `ClaimsActionSupport.nextId` | `JdbcTemplate` repositories issuing the same SQL against `PAYMENT` and `SETTLEMENT` |
| `struts-config.xml` action mappings and forwards | Spring MVC request mappings; forward names become the `screen` value |
| Global `java.lang.Exception` mapping to `error.jsp` | An exception handler returning `screen: error` with status 500 (PAY-R04, PAY-R07, PAY-R22) |

### Database

The service runs against HSQLDB in memory, created at startup by executing the
legacy `src/main/resources/db/schema.sql` and `src/main/resources/db/seed.sql`
by file path, exactly as the settlement service does. The seeded rows the
transcripts depend on (claim 119, settlement 119 — whose seeded `settlement_amount` is 0, so an
omitted `amount` issues 0.00 — and the 60 seeded payments that make the next
`payment_id` 61) are therefore identical to
the legacy fixture.

The only table written is `PAYMENT`. `SETTLEMENT` is read for the claim's
latest settlement (PAY-R03); `CLAIM` is never touched (PAY-R13).

### Out of scope

* **Login and `AuthFilter`** (PAY-R29). The service exposes its endpoints
  unauthenticated on 8084, as 8083 does. Consequence: 8084 is not
  internet-facing and fronting it with the existing authentication is a
  separate decision.
* **The Struts message bundle.** The service returns data, not labels.
* **Every other module** — policy, intake, workbench, reporting, and the
  settlement screens already served by 8083. The two services share nothing at
  runtime: each loads its own copy of the seed, so a settlement saved on 8083 is
  not visible to 8084. A shared database is a later decision.

### How parity is judged

A payment response is at parity with the legacy one when these match:

1. status class (2xx / 4xx / 5xx, not the exact Struts-forwarded 200; a legacy
   `error.jsp` forward is matched by any 5xx from the service, as in ADR-001),
2. the business field values (the `<ns:field>` values the transcripts record),
3. validation keys, where any are produced (none are, on this slice),
4. resulting database state in `PAYMENT`, read back through the service's own
   `/payment/detail` (`payment.<id>.amount`, `payment.<id>.status`) and
   `/payment/remittance` (`payment.count.claim.<id>` from `remitCount`).

HTML is explicitly not part of parity. `tools/parity/payment_parity.py` replays
`transcripts/payment_*.json` in the order of `transcripts/index.json` (issue
before history, because history expects payment 61 to exist) and writes its
rows into `parity/report.json` and `parity/report.md` next to the settlement
rows; `make parity` runs both.

### Behaviour freeze

Legacy behaviour is reproduced as-is. There are no approved differences for
this slice and `parity/routes.json` has no `approved_differences` entry for any
payment scenario; a payment row in the report must read `PASS`.

Amended 2026-10-15 by docs/changes/CHG-002-payment-exceeds-settlement.md: the
slice now has one approved difference, PAY-R05 (v2), registered in
`parity/routes.json` as `payment_issue_over_settlement`. A payment row reads
`PASS`, or `CHANGED (CHG-nnn)` when a change record approves it; anything else
is still a failure, as for the settlement slice (ADR-001).

## Consequences

The service has to reproduce these fallbacks and quirks:

* missing or unparsable `claimId` becomes 119 (PAY-R02);
* missing or unparsable `amount` becomes the claim's latest settlement amount
  (PAY-R05);
* `payeeName` and `paymentMethod` are stored verbatim, and a missing one fails
  the request rather than defaulting (PAY-R06, PAY-R07);
* a claim with no settlement fails the request (PAY-R04);
* `payment_id` is `max(payment_id) + 1` (PAY-R09), the check number is the
  unpadded `CHK-<paymentId>` (PAY-R10), `issued_date` is the frozen literal
  `2019-04-03` and `status` is `ISSUED` (PAY-R11);
* `/payment/detail` defaults `paymentId` to 61 and fails on an unknown id
  (PAY-R20, PAY-R22);
* `remitTotal` is the database sum, `remitCount` the list size (PAY-R26);
* no claim-status guard: payments are issuable against `DENIED` and `CLOSED`
  claims (PAY-R13).

Other consequences:

* `docs/TRACEABILITY.md` now has a row for every PAY-Rnn version as well as
  every SETTLE-Rnn version; `tools/traceability.py` reads both specs.
* `make payment-build`, `make payment-test` and `make payment-run` mirror the
  settlement targets; `make parity` fails if either slice fails.
