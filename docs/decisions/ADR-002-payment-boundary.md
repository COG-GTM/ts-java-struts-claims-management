# ADR-002: Payment slice as the second extracted service

Status: Accepted
Date: 2026-10-16

## Context

ADR-001 moved settlement out of the Struts monolith into
`services/settlement-service`. Payment is the next slice downstream: a payment
is issued against a claim's settlement. Its behaviour is written up in
`docs/specs/SPEC-PAY-001.md` (rules PAY-R01 to PAY-R20, all v1), backed by the
41 live requests of `docs/specs/SPEC-PAY-001-live.json` and by the two recorded
transcripts `payment_issue` and `payment_history`. The spec is the contract of
this service and is not changed by it.

## Decision

Extract the payment slice — `/payment/issue`, `/payment/history`,
`/payment/detail`, `/payment/remittance` — into a Spring Boot 3.5 / Java 21
service at `services/payment-service`, listening on port 8084, built the same
way as the settlement service.

### Where the boundary is

* **Inside:** the four payment actions, their JSPs' `<ns:field>` values and
  the `PAYMENT` table, which is the only table the slice writes (one insert, in
  `PaymentDAO.insert`).
* **Read across the boundary:** `SETTLEMENT`, for the newest settlement of the
  claim (`SettlementDAO.findByClaim`). The payment service reads it and never
  writes it. Saving a settlement stays with the settlement slice.
* **Outside:** login, the claim lifecycle and every other module. Claim status
  is not read at all, because the legacy issue path does not read it (PAY-R12).

### How each Struts piece maps

| Struts today | Payment service |
| --- | --- |
| `PaymentIssueAction`, `PaymentHistoryAction`, `PaymentDetailAction`, `PaymentRemittanceAction` | One `@RestController` (`PaymentController`) with four endpoints, each accepting GET and POST as the Struts mappings do |
| Raw `request.getParameter` reads through `ClaimsActionSupport.integer` / `decimal` | The same `getParameter` reads and the same lenient coercion (`LegacyParameters`): first value wins, unparseable ids fall back to 119 or 61 |
| `issue.jsp`, `history.jsp`, `detail.jsp`, `remittance.jsp` with `<ns:field>` | JSON `fields` with the same names and the same money, integer and text printing |
| The two fixed links of `history.jsp` | JSON `links`, in page order |
| Payee and method printed with `bean:write` (not fields) | JSON `display`, unescaped; escaping is the client's job, as it is the JSP's |
| `PaymentDAO`, `SettlementDAO.findByClaim`, `ClaimsActionSupport.nextId` | `PaymentRepository`, a `JdbcTemplate` repository issuing the same SQL |
| Global exception mapping to `error.jsp` | `PaymentErrorHandler`: screen `error`, no fields, status 500 |
| `AuthFilter` redirect to `/claims/login.do` | A `user` parameter standing in for the session user; without one, the same 302 to `/claims/login.do` and nothing runs |

### Database

As in ADR-001: HSQLDB in memory, created at startup from the monolith's own
`src/main/resources/db/schema.sql` and `seed.sql`, read by file path. The seeded
payments (ids 1 to 60), settlements 119 and 120 and claims 119 and 120 are
therefore the legacy fixture, and `max(payment_id) + 1` gives 61 for the first
payment exactly as the monolith does.

### Sign-in

ADR-001 left the settlement endpoints unauthenticated. Payment cannot do that
and still behave like the legacy app, because PAY-R13 says every payment
request without a session is redirected to login and an issue writes nothing.
The service therefore takes the signed-in user as an explicit `user` parameter
(the same move ADR-001 made for `savedBy`) and answers 302 with
`Location: /claims/login.do` when it is missing or empty. Checking who the user
is stays out of scope: like the legacy filter, the service only checks that
there is one. The `user` parameter is not authentication: any caller that
sends one is treated as signed in. Consequence: 8084 is not internet-facing, and
putting real authentication in front of it is a separate decision that must be
made before the service is reachable by anything but the monolith's network.

### How parity is judged

A payment response is at parity with the legacy one when these match:

1. status class; the legacy `error.jsp` forward with a 200 is matched by a 5xx,
   as in ADR-001, and a redirect must be a 302 to the same `Location`;
2. the business field values (`<ns:field>`), exactly, with no extra fields;
3. validation keys (the legacy payment screens produce none);
4. the in-page links of the history screen, in order;
5. payee and method as displayed (`display` values must appear on the legacy
   page);
6. the database: the `db_state` of the transcripts, and at the end of the live
   replay the four `db_after` queries of `SPEC-PAY-001-live.json`
   (`PAYMENT` column types, the `PAYMENT` rows written, the row count and
   highest id, and the settlements used).

`tools/parity/payment_parity.py` replays the requests against the service run
with the `parity` profile (`make payment-service-run-parity`), which adds
`/parity/reset`, `/parity/apply` and a select-only `/parity/query` and binds
the service to 127.0.0.1. The live
replay starts from the seed and runs the live steps in their recorded order,
because ids, check numbers and totals depend on what was issued before. Three
live steps are setup, not payment behaviour: L02 and L35 sign in (the service
takes `user` instead), and L05 saves a settlement, which belongs to the
settlement slice; parity applies the row it writes from
`parity/preconditions/payment/settlement-save.sql`. The transcripts run the same
way: seed, then that settlement row, then `payment_issue`, then
`payment_history`.

HTML is not part of parity, as in ADR-001.

### Behaviour freeze

ADR-001's rule applies unchanged: the legacy behaviour is reproduced as-is,
including the parts that look wrong, unless a `docs/changes/CHG-nnn` record
approves a difference. This ADR approves none, so every payment scenario has to
PASS.

## Consequences

The service reproduces, on purpose:

* `claimId` missing or unparseable is 119; `paymentId` missing or unparseable
  is 61;
* the amount falls back to the newest settlement's amount, and any amount that
  parses as a double is stored as sent: negative, above the settlement,
  unrounded, in exponent form, or NaN;
* `payment_id` is `max(payment_id) + 1` with no locking, so two concurrent
  issues can collide exactly as they can in the monolith;
* the check number is `CHK-<id>` and a `checkNumber` parameter is ignored;
  every payment is issued on 2019-04-03 with status ISSUED;
* payee and method are stored as sent, with no trimming, case change or list of
  methods;
* a missing payee or method, a method over 20 characters, or a claim without a
  settlement is the error screen and writes nothing;
* issuing is not restricted by HTTP method, claim status or role;
* history always shows the two `detail.do?paymentId=61` links;
* an unknown payment id on detail is the error screen;
* the remittance total sums every payment of the claim, so one NaN amount
  makes it NaN;
* money prints with `%.2f` in `Locale.US`, as the settlement service's
  `FieldFormatter` does: the transcripts and the live proof were captured on an
  en_US JVM, while the legacy `String.format` follows the host's default locale.

The service's database is its own in-memory copy of the seed, as in ADR-001.
During the transition it does not see settlements saved through the monolith
or the settlement service, or payments the monolith issues, and they do not see
its payments. A payment issued after a new settlement therefore links the
newest settlement the service knows, not the one just saved. The parity replay
supplies the L05 settlement row as a precondition for this reason. Pointing the
payment service, the settlement service and the monolith at one shared
database is a separate decision that must come before any cutover. The
settlement service remains the only extracted writer of `SETTLEMENT`; the
payment service only reads it.
