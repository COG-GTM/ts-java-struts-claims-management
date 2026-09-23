# ADR-001 — Settlement service boundary

Status: accepted

Date: 2026-09-23

Related: `docs/specs/SPEC-SETTLE-001.md` (v0.2),
`docs/analysis/settlement-slice.md`, `docs/specs/OPEN-QUESTIONS.md`

## Decision

Extract the settlement slice — the Struts mappings `/settlement/calculate`,
`/settlement/save` and `/settlement/detail` — into a single Spring Boot 3.5 /
Java 21 service in `services/settlement-service`, listening on port 8083. This
is the first module to be extracted; no other module moves until this one is in
place and at parity.

## Why this slice first

* It is the best-evidenced slice in the application. Seven captured transcripts
  cover it — six variants of `/settlement/calculate.do`
  (`settlement_calculate`, `settlement_blank_deductible`,
  `settlement_deductible_floor`, `settlement_policy_cap`,
  `settlement_half_cent`, `settlement_bad_deductible`) plus
  `settlement_save` — and they already pin the request parameters, the
  fallbacks, the boundary cases and the rendered fields.
* Its write surface is one table. The slice reads `CLAIM` and `POLICY` and
  writes exactly one row into `SETTLEMENT` on save; it updates nothing. A
  service that owns one insert can be run beside the legacy application without
  a write-conflict story.
* Its risk is concentrated in one rule. The whole slice has a single piece of
  genuinely subtle arithmetic — `Math.round(amount * 100.0) / 100.0` on a
  `double` (SETTLE-R12), which differs from the `%.2f` display rounding
  (SETTLE-R17). Getting one known rounding rule right is a tractable first
  parity target, and it is exactly the rule a naive `BigDecimal` rewrite would
  silently change.
* It has no workflow state. Unlike intake or the workbench, nothing in the
  slice advances a claim status or depends on an earlier request in the
  session, so each endpoint can be characterised in isolation.

## Mapping from legacy to service

| Legacy | New |
| --- | --- |
| `SettlementCalculateAction`, `SettlementSaveAction`, `SettlementDetailAction` (each `extends ClaimsActionSupport`) | One `SettlementController` with three handler methods, one per mapping |
| `struts-config.xml` action paths `/settlement/calculate`, `/settlement/save`, `/settlement/detail` | `POST /settlement/calculate`, `POST /settlement/save`, `GET /settlement/detail` on port 8083 |
| `settlementForm` bean, declared but never read; the actions call `request.getParameter` directly | An explicit request record bound from form parameters, with every field typed `String` so the legacy parse-and-fallback rules stay in the service and are not pre-empted by the framework's binder |
| `ClaimsActionSupport.integer` / `decimal` fallbacks | Explicit parse helpers on the request record, one per fallback rule, unit tested against the rules in SPEC-SETTLE-001 |
| `calculate.jsp`, `save.jsp`, `detail.jsp` rendering `<ns:field>` spans | A JSON screen response whose property names are the legacy field names (`coveredAmount`, `deductibleApplied`, `depreciation`, `cappedAtLimit`, `settlementAmount`, `savedBy`, `detail*`), carrying the same formatted strings the spans carry |
| `FieldTag` `money` / `integer` / `date` / `text` formatting | A formatting component applied when building the JSON response, reproducing `String.format("%.2f", ...)` including its pass-through of non-numeric values (SETTLE-R17, SETTLE-R18) |
| `SettlementCalculator` singleton and the `Settlement` bean | A `Settlement` domain class plus a stateless calculation service; the singleton and the `Log` field do not carry over |
| `SettlementDAO`, `PolicyDAO`, `ClaimDAO` with hand-rolled JDBC and `ConnectionPool` | `JdbcTemplate`-based repositories with the same SQL text, including `select ... where claim_id = ? order by settlement_id desc` and the `coalesce(max(settlement_id),0)+1` id allocation |
| `ActionForward` to a JSP, `global-exceptions` to `error.jsp` | Response status plus an error body; the legacy status-200-on-exception behaviour (SETTLE-R07) is reproduced, not corrected |

## Database

The service runs against HSQLDB in memory, initialised at startup from the
legacy DDL and data by file path — `src/main/resources/db/schema.sql` and
`src/main/resources/db/seed.sql` in the legacy tree — not from copies. The
files stay the single source of truth, so a change to the legacy schema or seed
is picked up by the service and by the characterisation tests without a
synchronisation step. No JPA entities, no schema generation, no migration tool
in this service.

## What is mocked or left out

| Left out | Consequence |
| --- | --- |
| Login and `AuthFilter` | The service has no authentication and accepts every request. Legacy `*.do` requests are redirected to `login.do` when unauthenticated; that redirect is out of parity scope, and the service must not be exposed outside the migration environment. `savedBy` / `calculatedBy` come from the session user in legacy (SETTLE-R22, SETTLE-R25), so the service takes the operator as an explicit request input and defaults it to `supervisor`, matching the transcripts. |
| The Struts message bundle (`ApplicationResources.properties`) | No labels, headings or `errors.*` text are served. Parity is judged on field values and validation keys, never on label text, so the bundle is not needed; any UI keeps using the legacy bundle. |
| JSP rendering, `ns:view`, page layout and navigation | No HTML is produced. Transcript comparison uses the field values, not the markup, so the `<span id="f_...">` wrapper has no successor; the capture harness gains a JSON reader for this service. |
| Every other module — intake, workbench, payments, policy, reporting | The service cannot follow the links the settlement screens render, and the `payment_issue` flow keeps reading `SETTLEMENT` from the legacy application. Both applications therefore point at the same database in the migration environment, and the settlement service remains the only writer of new `SETTLEMENT` rows. |
| `SettlementService.calculateAndSave` in the legacy tree (hard-coded `supervisor` / `2019-03-01`) | Not reachable from any mapping, so it is not carried over. If something turns out to call it, that is a new question for `docs/specs/OPEN-QUESTIONS.md`. |

## How parity is judged

A transcript passes when the service response matches the legacy transcript on
all four of:

1. **Status class** — the HTTP status family, so the legacy
   status-200-with-an-error-page case (SETTLE-R07) is matched by an error
   response in the 2xx class rather than by a 500.
2. **Business fields** — the same field names with the same formatted string
   values as the transcript's `business_fields`, compared exactly, including
   `1.01` for the covered amount and `1.00` for the settlement amount in
   `settlement_half_cent`.
3. **Validation keys** — the same `validation_errors` keys in the same order
   (the settlement slice has none today; the comparison still runs).
4. **Database state** — the same `db_state` probe values, and, for save, the
   inserted `SETTLEMENT` row compared column by column.

HTML is explicitly not compared: not markup, not element order, not labels, not
CSS classes. A difference that shows up only in rendering is not a parity
failure.

## Change control

Legacy behaviour is preserved by default, including behaviour that is plainly a
defect. A difference is permitted only when a record in `docs/changes/CHG-nnn`
approves it, naming the legacy behaviour, the new behaviour, and who accepted
the change. Any oddity that is preserved rather than fixed is recorded in
`docs/KNOWN_LEGACY_QUIRKS.md` with a pointer to its rule in SPEC-SETTLE-001, so
that the list of things the service does deliberately-wrong is explicit and
reviewable.

## Consequences

The service must reproduce, not repair, the following fallbacks and quirks.
Each is a named rule in SPEC-SETTLE-001:

* `claimId` defaults to `119` when absent or non-integer (SETTLE-R03).
* `coveredAmount` defaults to `5000` and `depreciation` to `0` when absent or
  unparseable (SETTLE-R04).
* The policy limit defaults to `10000`, set before the claim lookup, so an
  unknown claim or a missing policy still calculates — and the fallback can
  itself cap the result (SETTLE-R05v2). Whether this is intended is OQ-01; the
  interim decision is to preserve it and list it in the quirks register.
* A missing or blank `deductible` becomes `0` (SETTLE-R06), while a non-blank
  non-numeric `deductible` throws and yields the error page with HTTP 200
  (SETTLE-R07).
* The zero floor applies before the cap comparison, and the full deductible is
  still reported (SETTLE-R10, SETTLE-R11).
* `double` cent rounding via `Math.round(amount * 100.0) / 100.0`, which gives
  `1.00` for `1.005` (SETTLE-R12) while the display format gives `1.01`
  (SETTLE-R17). `BigDecimal` may be used internally only if it reproduces these
  two results exactly.
* `cappedAtLimit` is rendered through the `money` formatter and falls through
  unchanged as `true` / `false` (SETTLE-R18).
* Save recalculates from the request rather than reusing a calculated result,
  has no guard for a missing claim or policy, allocates its id with
  `max(settlement_id)+1`, and writes the literal date `2019-04-01`
  (SETTLE-R21, SETTLE-R22).
* Detail returns the highest `settlement_id` for the claim, and null when the
  claim has none (SETTLE-R28).

Characterisation tests for all of the above land in their own pull request,
merged before any service code is written. They run against the legacy
application and the transcripts, and they are the contract the new service is
then built to satisfy; the service pull request adds no new expectations to
them beyond those approved by a `CHG-nnn` record.

Further consequences: port 8083 is reserved for this service in the migration
environment alongside the legacy application on 8080; both share one database,
so the extraction is reversible by routing the three mappings back to Struts;
and until a UI consumes the JSON screen response, the legacy JSPs remain the
only rendering of these screens.
