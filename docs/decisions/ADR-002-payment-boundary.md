# ADR-002: Payment slice boundary

Status: Accepted
Date: 2026-10-20

## Context

The payment slice of the Struts 1.3.10 monolith is four action mappings,
`/payment/issue`, `/payment/history`, `/payment/detail` and
`/payment/remittance` (`src/main/webapp/WEB-INF/struts-config.xml:223-238`,
`:340-355`), served by `PaymentIssueAction`, `PaymentHistoryAction`,
`PaymentDetailAction` and `PaymentRemittanceAction`
(`src/main/java/com/northstar/claims/web/`). They write the `PAYMENT` table
through `PaymentDAO` and read the newest `SETTLEMENT` row of a claim through
`SettlementDAO.findByClaim` (`src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-115`).
The behaviour is specified by `docs/specs/SPEC-PAY-001.md` version 0.1
(26 rules, each with `Read:` and `Observed:` evidence), the transcripts
`transcripts/payment_issue.json` and `transcripts/payment_history.json`, and
the live proof `docs/specs/SPEC-PAY-001-proof.json` (21 cases recorded
against the running legacy app by `make payment-spec-proof`). ADR-001 and
`services/settlement-service` are the pattern copied here.

## Decision

Extract the slice into `services/payment-service`: Spring Boot 3.5 on Java 21,
port 8084 (the next free port after settlement's 8083), `JdbcTemplate` against
the same `PAYMENT` and `SETTLEMENT` tables. As in ADR-001 the service runs
HSQLDB in memory, created from the monolith's own
`src/main/resources/db/schema.sql` and `seed.sql` by file path, so its fixture
cannot drift from the legacy one. It reproduces SPEC-PAY-001 as written,
including behaviour that looks wrong. It does not call the settlement service.

### Endpoints

| Legacy (Struts) | Service |
| --- | --- |
| `GET`/`POST /claims/payment/issue.do` | `GET`/`POST /payment/issue` (GET writes too, as legacy does) |
| `GET /claims/payment/history.do` | `GET /payment/history` |
| `GET /claims/payment/detail.do` | `GET /payment/detail` |
| `GET /claims/payment/remittance.do` | `GET /payment/remittance` |

Request parameters keep their legacy names: `claimId`, `payeeName`, `amount`,
`paymentMethod`, `paymentId`. A submitted `checkNumber` is ignored, as in
legacy.

### Tables

* Written: `PAYMENT`, one row per successful issue, all nine columns
  (`PaymentDAO.java:140-159`). Nothing else is written.
* Read: `PAYMENT` (history, detail, remittance, next id) and `SETTLEMENT`
  (newest row of the claim, `order by settlement_id desc`).

### How each Struts piece maps

| Struts today | Payment service |
| --- | --- |
| Four `Action` classes extending `ClaimsActionSupport` | `PaymentController`, one method per mapping |
| `request.getParameter` with `ClaimsActionSupport.integer` / `decimal` fallbacks (`ClaimsActionSupport.java:31-45`) | `LegacyParameters.integer` / `decimal`, copied as written: `claimId` 119, `paymentId` 61, amount = newest settlement amount |
| `ClaimsActionSupport.nextId("PAYMENT")` (`ClaimsActionSupport.java:61-75`) | `PaymentRepository.nextId`: `select coalesce(max(payment_id),0)+1 from PAYMENT` |
| `PaymentDAO`, `SettlementDAO.findByClaim` | `PaymentRepository`, `SettlementRepository`, same SQL |
| `<ns:field>` values on `issue.jsp`, `history.jsp`, `detail.jsp`, `remittance.jsp`, formatted by `FieldTag` | JSON `fields`, keyed by the same names, in screen order, formatted by `FieldFormatter` (`%.2f`, `NaN` prints `NaN`) |
| `<bean:write>` of payee and method (`detail.jsp:14,18`, `remittance.jsp:19`) | JSON `display` (`payeeName`, `paymentMethod`, `remitPayee_<id>`) carrying the text the screen shows; escaping is the job of whatever renders it |
| The two `<html:link>` pairs of `history.jsp` | JSON `links`, always `detail.do?paymentId=61` and `remittance.do?claimId=<claim>`, twice |
| Forward name (`payment`, `history`, `detail`, `remittance`) | JSON `screen` (`issue`, `history`, `detail`, `remittance`) |
| Global exception mapping to `error.jsp` (200) | `PaymentErrorHandler`: 500, `screen` `error`, no fields; failed inserts write nothing |
| `AuthFilter` redirect to `/claims/login.do` without a session | `OperatorFilter`: 302 to `/claims/login.do` without an operator, before any controller runs |
| Session user shown in the audit table (`Operator`) | JSON `operator`, from the `X-NorthStar-Operator` header |

### Operator identity (authentication out of scope)

The service does not authenticate. Login, sessions and roles stay in the
monolith (or a gateway in front of the service), which passes the signed-in
operator in the `X-NorthStar-Operator` header. A request without the header is
answered exactly as legacy answers a request without a session: 302 to
`/claims/login.do`, nothing written. Legacy lets any signed-in operator issue a
payment; so does the service.

### How parity is judged

A payment response is at parity with the legacy one when these match, step by
step, for the same requests in the same order on the same starting database:

1. status: equal, except that the legacy `error.jsp` forward (200) is matched
   by a 5xx with `screen` `error` (as ADR-001), and a 302 is matched by a 302
   to the same path;
2. the screen;
3. the `<ns:field>` values, compared as an ordered list (row order is a rule);
4. the payee and method texts, and the history links (without the menu link
   every screen carries);
5. the operator;
6. validation keys (always none: every payment mapping has `validate="false"`);
7. the database: every query the legacy case read afterwards returns the same
   rows on the service, values read as `ResultSet.getString`.

Each scenario starts from the legacy schema and seed plus its own explicit
precondition in `parity/preconditions/payment.sql`, applied by the service's
loopback-only `/parity/reset` (enabled only by `make payment-run-parity`). HTML
markup, span ids, the menu and the message bundle are presentation and are not
reproduced. `make parity` writes the verdicts to `parity/report.md`,
`report.json` and `report.html`; a verdict that relies on rule 1's error
equivalence carries the Reviewer mark.

### Behaviour freeze

Legacy behaviour is reproduced as-is. A difference is allowed only when a
`docs/changes/CHG-nnn` record approves it and `parity/routes.json` lists it;
this ADR approves none. Kept oddities are listed in
`docs/KNOWN_LEGACY_QUIRKS.md`.

## Consequences

* The service keeps: GET writes; claim 119 and payment 61 fallbacks; amounts
  stored as parsed (negative, exponent, `NaN`, `1.005`); the fixed issued date
  `2019-04-03`; `CHK-<id>` check numbers; next id over the whole table; no
  business checks (closed claims, overpayment, repeated requests); totals over
  every status; the fixed `detail.do?paymentId=61` link.
* Its database is its own in-memory copy of the seed: payments issued through
  the service are not visible to the monolith and are lost on restart. A shared
  database is a later decision, as for settlement.
* Callers must send the operator header; the gateway that sets it is not part
  of this ADR.
