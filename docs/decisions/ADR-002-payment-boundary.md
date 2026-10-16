# ADR-002: Payment slice as the second extracted service

Status: Accepted
Date: 2026-10-16

## Context

ADR-001 moved the settlement slice out of the Struts 1.3.10 / Java 7 monolith
into `services/settlement-service` (port 8083) and established how an
extraction is specified (`docs/specs/SPEC-*.md`), judged (`make parity`) and
traced (`make traceability`). The payment module is the natural next slice: it
is the only consumer of a saved settlement, it is covered by two recorded
transcripts (`payment_issue`, `payment_history`), and its rules are written up
in `docs/specs/SPEC-PAY-001.md` (version 0.1, 30 rules, PAY-R01 to PAY-R30),
each proved either by a transcript or by the live run recorded in
`docs/specs/SPEC-PAY-001-live.json` (steps L01-L47 and a read-only JDBC probe
of the rows the run wrote).

## Decision

Extract the payment slice — `/payment/issue`, `/payment/history`,
`/payment/detail`, `/payment/remittance` — into a single Spring Boot 3.5 /
Java 21 service at `services/payment-service`, listening on port 8084, built
the same way as the settlement service, and behaving exactly as SPEC-PAY-001
says the monolith behaves. No `CHG-nnn` record is approved for this slice, so
every rule, including the ones that look like defects, is reproduced.

### Why this slice next

* It is covered by two of the 22 transcripts, so parity has a recorded
  definition for the write path (issue) and the read path (history), and the
  live run adds 45 more recorded requests with their legacy responses.
* It writes to exactly one table, `PAYMENT`, and only reads `SETTLEMENT`
  (PAY-R05, PAY-R16), so the ownership boundary is clean.
* It reuses the settlement service's recipe (in-memory HSQLDB from the legacy
  scripts, `JdbcTemplate`, JSON with the `<ns:field>` names), so the second
  extraction tests that the recipe is repeatable rather than bespoke.

### Endpoints

| Legacy path | Service endpoint | Writes |
| --- | --- | --- |
| `POST` or `GET /claims/payment/issue.do` (PAY-R02, PAY-R03) | `POST` or `GET /payment/issue` (`claimId`, `payeeName`, `amount`, `paymentMethod`) | `PAYMENT` |
| `GET /claims/payment/history.do` (PAY-R18) | `GET /payment/history` (`claimId`) | nothing |
| `GET /claims/payment/detail.do` (PAY-R22) | `GET /payment/detail` (`paymentId`) | nothing |
| `GET /claims/payment/remittance.do` (PAY-R25) | `GET /payment/remittance` (`claimId`) | nothing |

Every response has the same shape as the settlement service's: `screen` (the
legacy forward: `issue`, `history`, `detail`, `remittance`, or `error`),
`fields` (the `<ns:field>` values, already formatted as the tag would print
them, PAY-R28) and `errors` (always empty: PAY-R11, there is no Struts
validation on the slice).

### Tables written

`PAYMENT` only, by one insert per successful issue (PAY-R16). `SETTLEMENT` is
read for the claim's newest settlement (PAY-R05) and never written. `CLAIM` is
never touched. The service has no sequence: `payment_id` is
`max(payment_id)+1` read before the insert, exactly as
`ClaimsActionSupport.nextId` does (PAY-R14), so a failed insert consumes no
identifier (PAY-R13) and the concurrency gap of OQ-08 is carried over.

### How each Struts piece maps

| Struts today | Payment service |
| --- | --- |
| `PaymentIssueAction`, `PaymentHistoryAction`, `PaymentDetailAction`, `PaymentRemittanceAction` (`extends ClaimsActionSupport`) | One `@RestController` (`PaymentController`) with four endpoints |
| `struts-config.xml` mapping of `/payment/issue` with no method restriction | `@RequestMapping(method = {POST, GET})`, so a GET issues a payment too (PAY-R03) |
| `PaymentForm` + raw `request.getParameter` reads | Request parameters read as strings with the same lenient coercion (`integer`, `decimal` of `ClaimsActionSupport`), kept in `LegacyParameters` (PAY-R04, PAY-R06, PAY-R07, PAY-R08, PAY-R18, PAY-R22, PAY-R25); the form's `checkNumber` is ignored as it is today (PAY-R11) |
| `issue.jsp`, `history.jsp`, `detail.jsp`, `remittance.jsp` with `<ns:field>` | JSON `fields` with the same names: `paymentAmount`, `checkNumber`, `paymentStatus` (PAY-R17); `checkNumber_<id>`, `paymentAmount_<id>`, `paymentStatus_<id>` (PAY-R19); `detailPaymentId`, `detailPaymentClaim`, `detailPaymentAmount`, `detailPaymentCheck`, `detailPaymentDate`, `detailPaymentStatus` (PAY-R24); `remitCheck_<id>`, `remitDate_<id>`, `remitAmount_<id>`, `remitStatus_<id>`, `remitClaimId`, `remitCount`, `remitTotal` (PAY-R26, PAY-R27) |
| `<bean:write>` cells for payee and method on detail.jsp and remittance.jsp | Not answered: they carry no `<ns:field>` id, so they are not business fields (PAY-R24 records them as plain HTML-escaped cells) |
| `FieldTag` `money` / `integer` / `date` formatting and HTML escaping | `FieldFormatter` applying the same `String.format("%.2f")`, long cast and `yyyy-MM-dd` rules (PAY-R28); no HTML escaping, the values are JSON |
| `PaymentDAO`, `SettlementDAO.findByClaim` + `ConnectionPool` + `ClaimsActionSupport.nextId` | `JdbcTemplate` repositories (`PaymentRepository`, `SettlementLookupRepository`) issuing the same SQL against `PAYMENT` and `SETTLEMENT` |
| `struts-config.xml` forwards | Forward names become the `screen` value |
| Global `java.lang.Exception` mapping to `error.jsp` (PAY-R29) | `PaymentErrorHandler` returning `screen: error`, no fields, status 500 (missing settlement PAY-R05, schema constraint violation PAY-R12, missing payment PAY-R23) |
| Screen-audit table (PAY-R30), link paragraphs (PAY-R20) | Presentation, not reproduced |

### Database

The service runs against HSQLDB in memory, created at startup by executing the
legacy `src/main/resources/db/schema.sql` and `src/main/resources/db/seed.sql`
by file path, exactly as the settlement service does. The seeded rows the
rules depend on (payments 1-60 so the next id is 61, settlements 118, 119 and
120 with amounts 1118, 0 and 0) are therefore identical to the legacy fixture.

The `parity` Spring profile (`make payment-run-parity`) adds two endpoints that
are not part of the slice: `POST /parity/reset`, which re-executes the two
legacy scripts and then the precondition scripts named in the request from
`parity/preconditions/payment`, and `GET /parity/payment/<id>`, the stored row
unformatted. They exist only so that every parity scenario can state its
database precondition explicitly and check what the screens round away; the
profile listens on 127.0.0.1 only, and `make payment-run` and the service
tests do not have it.

### Out of scope

* **Login and `AuthFilter`** (PAY-R01). The service exposes its endpoints
  unauthenticated on 8084, as 8083 does. Consequence: 8084 is not
  internet-facing and fronting it with the existing authentication is a
  separate decision. PAY-R01 therefore has no parity scenario.
* **The Struts message bundle, the screen-audit table and the link
  paragraphs** (PAY-R17's message text, PAY-R20, PAY-R30). The service returns
  data, not labels; the hard-coded `detail.do?paymentId=61` link is a screen
  concern recorded in `docs/KNOWN_LEGACY_QUIRKS.md`.
* **Every other module** — policy, intake, workbench, reporting, and the
  settlement screens already served by 8083. The two services share nothing at
  runtime: each loads its own copy of the seed, so a settlement saved on 8083 is
  not visible to 8084. A shared database is a later decision; until then the
  "newest settlement" of PAY-R05 is the newest in the payment service's own
  copy.

### How parity is judged

A payment response is at parity with the legacy one when these match:

1. status class (2xx / 4xx / 5xx, not the exact Struts-forwarded 200; a legacy
   `error.jsp` forward is matched by any 5xx from the service, as in ADR-001),
2. the business field values (the `<ns:field>` values the transcript or the
   live run recorded),
3. validation keys, where any are produced (none are, on this slice),
4. resulting database state in `PAYMENT`, read back through the service's own
   `/payment/detail` (`payment.<id>.amount`, `payment.<id>.status`) and
   `/payment/remittance` (`payment.count.claim.<id>` from `remitCount`), and,
   for the unrounded stored amount (PAY-R09) and the settlement the payment is
   tied to (PAY-R05), raw from the parity profile's `GET /parity/payment/<id>`
   (`payment.<id>.stored_amount`, `payment.<id>.settlement_id`), compared with
   the live run's JDBC probe.

HTML is explicitly not part of parity. The scenarios are the
`payment_scenarios` of `parity/routes.json`: the two transcripts, replayed
under the database state the capture had when they were recorded
(`parity/preconditions/payment/capture-state*.sql`: `settlement_save` had
already written settlement 121 for claim 119), and 42 requests copied verbatim
from `docs/specs/SPEC-PAY-001-live.json`, each replayed under the state the
live run had at that step (`live-after-Lnn.sql`, rows copied from the run's
JDBC probe). `tools/parity/payment_parity.py` resets the service to the
scenario's precondition, sends the request, compares, and writes its rows into
`parity/report.json`, `parity/report.md` and the interactive
`parity/report.html` next to the settlement rows; `make parity` runs both
slices.

### Behaviour freeze

Legacy behaviour is reproduced as-is, including behaviour that looks wrong. A
difference is only allowed when a record under `docs/changes/CHG-nnn` approves
it; without such a record, the legacy behaviour wins. No such record exists for
the payment slice, so every payment scenario must be PASS. Legacy oddities that
are deliberately kept are listed in `docs/KNOWN_LEGACY_QUIRKS.md`.

## Consequences

The service has to reproduce these fallbacks and quirks:

* missing or unparsable `claimId` becomes 119 on issue, history and remittance
  (PAY-R04, PAY-R18, PAY-R25); missing or unparsable `paymentId` becomes 61 on
  detail (PAY-R22);
* a GET to `/payment/issue` writes a payment (PAY-R03);
* the payment is tied to the claim's newest settlement, and a claim without one
  cannot be paid (PAY-R05);
* missing or unparsable `amount` becomes the newest settlement's amount,
  including 0 (PAY-R06); anything `Double.parseDouble` accepts is accepted,
  including negative numbers, exponents, surrounding spaces and NaN (PAY-R07,
  PAY-R08); the amount is stored unrounded (PAY-R09);
* `payeeName` and `paymentMethod` are stored verbatim, including the empty
  string, and are checked only by the schema's NOT NULL and VARCHAR lengths
  (PAY-R10, PAY-R12); a `checkNumber` parameter is ignored (PAY-R11);
* `payment_id` is `max(payment_id)+1`, the check number `CHK-<id>` unpadded
  (PAY-R14); `issued_date` is the literal `2019-04-03` and `status` `ISSUED`
  (PAY-R15);
* a missing payment on detail is an error, so the default `paymentId=61` is an
  error until payment 61 exists (PAY-R23);
* the remittance total is the database `sum`, so negative amounts reduce it and
  one NaN makes it NaN (PAY-R27).

Other consequences:

* Port 8084 is added to the local run instructions; the monolith keeps 8080 and
  the settlement service 8083.
* `make parity` needs both services up (`make service-run` and
  `make payment-run-parity`); `make traceability` covers both specs.
* The open questions OQ-02 to OQ-09 in `docs/specs/OPEN-QUESTIONS.md` stay
  open; the service takes the interim decision recorded on each row.
