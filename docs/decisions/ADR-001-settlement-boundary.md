# ADR-001: Settlement slice as the first extracted service

Status: Accepted
Date: 2026-09-23

## Context

The claims application is a single Struts 1.3.10 / Java 7 web application
(`src/main/java/com/northstar/claims`, `src/main/webapp/WEB-INF/struts-config.xml`).
Its behaviour is pinned by 22 recorded transcripts under `transcripts/`, replayed
by `tools/capture/capture.py`, and the settlement rules are written up in
`docs/specs/SPEC-SETTLE-001.md` with the slice analysis in
`docs/analysis/settlement-slice.md`.

We need a first slice to move out of the monolith, small enough to finish and
verify, and load-bearing enough to prove the approach.

## Decision

Extract the settlement slice — `/settlement/calculate`, `/settlement/save`,
`/settlement/detail` — into a single Spring Boot 3.5 / Java 21 service at
`services/settlement-service`, listening on port 8083, before any other module
is touched.

### Why this slice first

* It is covered by six of the 22 transcripts (`settlement_calculate`,
  `settlement_policy_cap`, `settlement_blank_deductible`,
  `settlement_deductible_floor`, `settlement_half_cent`, `settlement_save`),
  so parity has a concrete, already-recorded definition.
* It writes exactly one table, `SETTLEMENT`, and only through one insert
  (`SettlementDAO.save`). Everything else on the slice is read-only, so the
  rollback surface is one table.
* Its business logic is one rounding rule —
  `Math.round(amount * 100.0) / 100.0` in `SettlementCalculator` — plus a
  deductible floor and a policy cap. That is small enough to reproduce exactly,
  including the half-cent case where 1.005 settles at 1.00.
* Its inputs are four request parameters and its output is five business
  fields, so the contract of the new service is tiny.

### How each Struts piece maps

| Struts today | Settlement service |
| --- | --- |
| `SettlementCalculateAction`, `SettlementSaveAction`, `SettlementDetailAction` (`extends ClaimsActionSupport`) | One `@RestController` with three endpoints |
| `SettlementForm` + raw `request.getParameter` reads | Request binding onto a request record, keeping the string-typed, lenient coercion of the actions (the actions ignore the form bean today) |
| `calculate.jsp`, `save.jsp`, `detail.jsp` with `<ns:field>` | JSON response objects with the same field names (`coveredAmount`, `deductibleApplied`, `depreciation`, `cappedAtLimit`, `settlementAmount`, `savedBy`) |
| `SettlementCalculator` singleton | A domain class holding the same arithmetic, injected as a normal bean |
| `SettlementDAO` + `ConnectionPool` + `ClaimsActionSupport.nextId` | A `JdbcTemplate` repository issuing the same SQL |
| `struts-config.xml` action mappings and forwards | Spring MVC request mappings; forward names become response shape, not view names |
| Global exception mapping to `error.jsp` | An exception handler returning the equivalent error status class |

### Database

The service runs against HSQLDB in memory, created at startup by executing the
legacy `src/main/resources/db/schema.sql` and `src/main/resources/db/seed.sql`
by file path. The scripts are not copied or rewritten: the service reads the
monolith's files, so seeded rows the transcripts depend on (policy 9001 with
limit 1000, policy 9002 with limit 100000, claims 119 and 120, settlements 119
and 120) stay identical to the legacy fixture, and schema drift is impossible by
construction.

### Out of scope

* **Login and `AuthFilter`.** The service exposes its endpoints unauthenticated
  on 8083. Nothing in the settlement calculation depends on the session except
  `calculated_by` / `savedBy`, which the service takes as supplied input instead
  of reading a session attribute. Consequence: 8083 is not internet-facing and
  fronting it with the existing authentication is a separate decision.
* **The Struts message bundle** (`ApplicationResources.properties`). The service
  returns data, not localised labels, so the bundle is not ported. Consequence:
  any UI over the new service owns its own labels, and parity is judged on
  validation keys rather than rendered messages.
* **Every other module** — policy, intake, workbench, payment, reporting. They
  stay in the monolith and keep using the same database. Consequence: two
  writers exist against `SETTLEMENT`'s neighbouring tables during the
  transition, and the monolith's settlement screens remain the ones operators
  use until a later cutover decision.

### How parity is judged

A settlement response is at parity with the legacy one when these match:

1. status class (2xx / 4xx / 5xx, not the exact Struts-forwarded 200),
2. the business field values (the `<ns:field>` values the transcripts record),
3. validation keys, where any are produced,
4. resulting database state in `SETTLEMENT`.

HTML is explicitly not part of parity: the legacy markup, span ids, forward
paths, and the message bundle are presentation and are not reproduced.

### Behaviour freeze

Legacy behaviour is reproduced as-is, including behaviour that looks wrong. A
difference is only allowed when a record under `docs/changes/CHG-nnn` approves
it; without such a record, the legacy behaviour wins. Legacy oddities that are
deliberately kept are listed in `docs/KNOWN_LEGACY_QUIRKS.md`, so that keeping
them is a recorded choice rather than an accident.

## Consequences

The service has to reproduce these fallbacks and quirks:

* missing or unparsable `claimId` becomes 119;
* missing or unparsable `coveredAmount` becomes 5000;
* missing or unparsable `depreciation` becomes 0;
* null or empty `deductible` is treated as 0, while a non-empty non-numeric
  `deductible` fails the request (legacy reaches `error.jsp`);
* a missing claim, or a claim whose policy is missing, gives a policy limit of
  10000 on the calculate path — verified live, and open as OQ-01 in
  `docs/specs/OPEN-QUESTIONS.md`;
* the save path has no such fallback: a missing claim fails the request;
* order of operations: depreciation, deductible, floor at zero, cap at the
  policy limit, then rounding;
* rounding is `Math.round(amount * 100.0) / 100.0` on the settlement amount
  only, so 1.005 settles at 1.00 while the echoed covered amount formats as
  1.01;
* `coveredAmount`, `deductibleApplied` and `depreciation` are echoed and
  persisted unrounded;
* `settlement_id` is allocated as `max(settlement_id) + 1`;
* `calculated_date` is the frozen literal `2019-04-01`;
* `/settlement/detail` returns the highest `settlement_id` for the claim, and
  nothing when the claim has no settlement row;
* no claim-status guard: settlements are calculable and savable against `DENIED`
  and `CLOSED` claims.

Other consequences:

* Characterisation tests for the legacy behaviour land in their own pull
  request, merged before the service pull request, so the service is written
  against tests that already pass on the monolith.
* Port 8083 is added to the local run instructions; the monolith keeps 8080.
* Java 21 and Spring Boot 3.5 enter the build for the new module only; the
  monolith stays on its Java 7 target.
