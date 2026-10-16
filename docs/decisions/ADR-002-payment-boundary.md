# ADR-002: Payment slice as the second extracted service

Status: Accepted
Date: 2026-10-16

## Context

The payment slice of the Struts 1.3.10 monolith is four action mappings under
`/payment/` (`src/main/webapp/WEB-INF/struts-config.xml:223-231`): issue,
history, detail and remittance, served by `PaymentIssueAction`,
`PaymentHistoryAction`, `PaymentDetailAction` and `PaymentRemittanceAction`
(`src/main/java/com/northstar/claims/web/`), reading and writing the `PAYMENT`
table through `PaymentDAO` and reading the newest `SETTLEMENT` row through
`SettlementDAO.findByClaim` (`src/main/java/com/northstar/claims/dao/`). Its
behaviour is pinned by `docs/specs/SPEC-PAY-001.md` (PAY-R01 to PAY-R20), the
two recorded transcripts `transcripts/payment_issue.json` and
`transcripts/payment_history.json`, and the 41-step live proof
`docs/specs/SPEC-PAY-001-live.json`. The settlement slice (ADR-001,
`services/settlement-service`) is the pattern this decision copies; it is not
special, and nothing in it changes here.

## Decision

Extract the payment slice into `services/payment-service`, a Spring Boot 3.5
application on Java 21 listening on port 8084 (the next free port after
settlement's 8083), with a `JdbcTemplate` against HSQLDB and the same
`PAYMENT` and `SETTLEMENT` tables, loaded from the legacy
`src/main/resources/db/schema.sql` and `seed.sql` by file path. The service
reproduces SPEC-PAY-001 as written, including the behaviour that looks wrong
(negative and NaN amounts, the fixed `detail.do?paymentId=61` link, the literal
`2019-04-03` issued date). It does not depend on the settlement service.

### Why this slice second

- It writes one table, `PAYMENT`, and reads one other, `SETTLEMENT`
  (`PaymentIssueAction.java:23-36`; `PaymentDAO.java`); nothing in the
  monolith reads `PAYMENT` outside the four payment actions.
- It has no `ActionForm` reads and no Struts validation (`validate="false"`,
  `struts-config.xml:223-231`), so the request surface is the four parameter
  names the actions read directly.
- Its behaviour is already specified and proved (SPEC-PAY-001, 20 rules, each
  with `Observed:` and `Read:`), so parity can be judged scenario by scenario.

### How each Struts piece maps

| Struts today | Payment service |
| --- | --- |
| `PaymentIssueAction`, `PaymentHistoryAction`, `PaymentDetailAction`, `PaymentRemittanceAction` (`extends ClaimsActionSupport`) | One `@RestController` (`PaymentController`) with four endpoints |
| `POST`/`GET /claims/payment/issue.do` | `POST`/`GET /payment/issue` (PAY-R12: both methods, as the Struts mapping accepts both) |
| `GET /claims/payment/history.do` | `GET /payment/history` |
| `GET /claims/payment/detail.do` | `GET /payment/detail` |
| `GET /claims/payment/remittance.do` | `GET /payment/remittance` |
| Raw `request.getParameter` reads with `ClaimsActionSupport.integer` / `decimal` fallbacks (`ClaimsActionSupport.java:31-45`) | `LegacyParameters.integer` / `decimal`, copied verbatim: claimId 119, paymentId 61, amount = newest settlement amount |
| `issue.jsp`, `history.jsp`, `detail.jsp`, `remittance.jsp` with `<ns:field>` | JSON `fields` keyed by the same names (`paymentAmount`, `checkNumber`, `paymentStatus`; `checkNumber_<id>`, `paymentAmount_<id>`, `paymentStatus_<id>`; `detailPayment*`; `remit*`) and formatted by `FieldFormatter` as `FieldTag.formatValue` does (`FieldTag.java:48-76`) |
| `<bean:write>` of payee and method on `detail.jsp:14,18` and `remittance.jsp` | JSON `display` (`payee`, `method`; `payee_<id>`), HTML-escaped as `<bean:write>` escapes them (PAY-R18) |
| The two `<html:link>` pairs of `history.jsp:16-19` | JSON `links`, always `detail.do?paymentId=61` and `remittance.do?claimId=<claimId>` twice (PAY-R15) |
| `PaymentDAO`, `SettlementDAO.findByClaim`, `ClaimsActionSupport.nextId` | `PaymentRepository`, `SettlementRepository` issuing the same SQL (`order by payment_id`, `order by settlement_id desc`, `coalesce(max(payment_id),0)+1`, `coalesce(sum(amount),0)`) |
| `struts-config.xml` action mappings and forwards | Spring MVC request mappings; the forward name becomes the `screen` value of the response (`issue`, `history`, `detail`, `remittance`, `error`) |
| Global exception mapping to `error.jsp` (`struts-config.xml:59-60`) | `PaymentErrorHandler` returning status 500 with `screen` `error` and no fields (PAY-R10, PAY-R11, PAY-R17) |
| `AuthFilter` session redirect to `/claims/login.do` (PAY-R13) | Not reproduced: see Out of scope |

### Database

The service runs HSQLDB in memory and executes the monolith's own
`schema.sql` and `seed.sql` at start-up (`application.properties`,
`legacy.db.dir`), so the fixture cannot drift from the legacy one. It writes
only `PAYMENT` (`PaymentIssueAction.java:36`) and reads `PAYMENT` and
`SETTLEMENT`. It never writes `SETTLEMENT`.

This database is the service's own. It is not the legacy application's
`target/db/northstar` file and it is not the settlement service's in-memory
database: a settlement saved through `services/settlement-service` is not
visible to the payment service, and a payment issued here is not visible to the
legacy history screen. SPEC-PAY-001 depends on exactly one such cross-slice
write, live L05 (settlement 121 for claim 119 at 1000.00, saved through the
legacy settlement screen before the issue requests); parity reproduces it as an
explicit precondition (`parity/preconditions/payment/*.sql`), never by calling
the settlement service. Sharing one database between slices is a later
decision, not this one.

### Out of scope

- Authentication and the session. PAY-R13 (the `AuthFilter` 302 to
  `/claims/login.do`, `AuthFilter.java:37-50`) belongs to the monolith's
  servlet filter, not to the payment actions, and the service has no login,
  no session and no operator parameter: none of the four payment actions reads
  the session user (`PaymentIssueAction.java:22-36`). The service therefore
  must only be reached from inside the deployment or behind the monolith's
  filter; it is not safe to expose directly. Live L01, L38, L39 and L40 are
  evidence for the monolith and are not parity scenarios.
- The HTML, the message bundle, the "Return to login" link on `error.jsp` and
  the audit block on `issue.jsp` that prints the operator name.
- The settlement screens the live proof used for set-up (L02, L05, L35).
- Any other table and any other slice.

### How parity is judged

A payment response is at parity with the legacy one when these match:

1. status class (2xx / 5xx, not the exact Struts-forwarded 200: the
   `error.jsp` forward is a 200 in the monolith and a 500 here),
2. the business field values (the `<ns:field>` values the transcripts and the
   live proof record), as an exact set: no missing and no extra field,
3. the `display` texts (payee, method) where the live proof recorded them
   (`payee_markup`, `text`), and the `links` of history,
4. validation keys, where any are produced (none on this slice, PAY-R01),
5. resulting database state in `PAYMENT`: the rows the live proof recorded in
   `db_after.payment_rows_after_seed`, compared as the JDBC driver prints them
   (`1000.0E0`, `NaN`), and the transcripts' `db_state` keys.

Every scenario names its precondition: `seed` (the legacy seed, 60 payments,
settlement 119 at 0 for claim 119) or a `parity/preconditions/payment/*.sql`
file generated by `tools/parity/payment_scenarios.py` from the live
proof, applied by `POST /parity/reset` before the request is replayed. The
reset and row-dump endpoints exist only under the `parity` Spring profile
(`make payment-run-parity`) and answer loopback callers only; the service
started by `make payment-run` does not have them.

HTML is explicitly not part of parity: the legacy markup, span ids, forward
paths and the message bundle are presentation and are not reproduced.

### Behaviour freeze

Legacy behaviour is reproduced as-is, including behaviour that looks wrong. A
difference is only allowed when a record under `docs/changes/CHG-nnn` approves
it, and `make parity` then reports the scenario as CHANGED against that record
instead of PASS. No payment scenario is CHANGED: there is no approved change
for this slice, so every one of the twenty rules is reproduced literally,
including PAY-R05 (NaN, negative and above-settlement amounts), PAY-R12
(payments on DENIED and CLOSED claims) and PAY-R15 (the fixed detail link).
A deliberately kept oddity is listed in `docs/KNOWN_LEGACY_QUIRKS.md` when one
is first discussed; this decision keeps them without discussion.

## Consequences

- `services/payment-service` can be built, tested and run on its own
  (`make payment-build`, `make payment-test`, `make payment-run`), and
  `make parity` replays every payment scenario in `parity/routes.json` against
  it with an explicit precondition each, next to the settlement scenarios.
- `docs/TRACEABILITY.md` gains a row per PAY rule, mapping it to the tests in
  `services/payment-service/src/test` and the parity scenarios that prove it,
  without changing the SETTLE rows.
- The legacy `src/main` is untouched; the monolith still serves `/payment/`
  exactly as before, and the two can run side by side (8080 and 8084) for
  comparison.
- The two slices hold separate databases and no cross-slice read is
  implemented; a settlement saved in one is not a settlement the other sees.
  Wiring them to one store, and fronting the services with the monolith's
  authentication, are the next decisions.
