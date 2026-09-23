# ADR-001: extract the settlement slice into a Spring Boot service

Status: accepted
Date: 2026-09-20
Applies to: SPEC-SETTLE-001

## Context

The Struts 1.3.10 application (`src/main/java/com/northstar/claims`) mixes
request parsing, business arithmetic and JDBC in the action classes.
`SettlementCalculateAction` reads request parameters, looks up `CLAIM` and
`POLICY`, calls `SettlementCalculator`, and forwards to `calculate.jsp`.
The README says modernisation work belongs in a separate target and must not
rewrite this baseline in place.

## Decision

1. One vertical slice first: settlement calculate, save and detail
   (`/settlement/calculate.do`, `/settlement/save.do`,
   `/settlement/detail.do`). It has arithmetic worth writing down (floor, cap,
   rounding), one table it writes (`SETTLEMENT`), two it reads (`CLAIM`,
   `POLICY`), and six captured transcripts.
2. Target: Spring Boot 3.5, Java 21, `services/settlement-service`, a Maven
   module inside this repository so specification, tests, code and matrix
   version together. The module has its own `pom.xml`; the legacy build is
   untouched.
3. Mapping: `SettlementCalculateAction` becomes `SettlementController`
   (HTTP) plus `SettlementService` (rules SETTLE-R02 to R09) plus
   `SettlementCalculator` (pure arithmetic, SETTLE-R07 to R09). Raw JDBC
   becomes `JdbcTemplate` repositories. The JSP becomes a JSON screen
   response that carries the same field names as the `<span id="f_...">`
   elements, so transcripts compare directly.
4. Database: HSQLDB in memory, initialised from the legacy
   `src/main/resources/db/schema.sql` and `seed.sql` by file path. Same engine,
   same seed, no copy of the SQL. A production datasource is out of scope for
   the slice.
5. What is mocked or left out: login and `AuthFilter` (the service has no
   session; the operator name is a configuration property defaulting to
   `supervisor`), the Struts message bundle (validation keys are returned as
   keys, not text), and the other modules (policy, intake, workbench, payment,
   reporting).
6. Parity is judged on status class, business fields, validation keys and
   database state, not on HTML. The harness is `parity/replay.py`; the
   fixtures are the existing `transcripts/settlement_*.json`.
7. Legacy behaviour is preserved unless a change record
   (`docs/changes/CHG-nnn-*.md`) approves a difference. Preserved oddities go
   into `docs/KNOWN_LEGACY_QUIRKS.md`.
8. No authentication and no input-length limit in the slice, on purpose.
   Pull request review flagged both; the reasons for leaving the code as it
   is:
   * Authentication (OQ-04). The legacy application authenticates in
     `AuthFilter` with a servlet session, one layer outside the actions the
     slice ports; the settlement actions themselves only read the user name
     from the session for `calculated_by`. Porting the filter would mean
     porting login, the `USERS` table and the session, which are a different
     slice with their own transcripts (`login.json`). Adding a different
     mechanism (tokens, a gateway) would be a design decision the parity
     harness cannot check, because no transcript exercises it, and it would
     change the request shape every transcript replays. The service is
     therefore reached only by the parity harness and its tests, is not
     deployed, and takes the operator name from configuration. Where
     authentication lives at deployment is recorded as OQ-04 and blocks
     deployment, not the slice.
   * Input length (OQ-05). The legacy screen has no length or syntax limit
     beyond `Double.parseDouble`, and the specification (SETTLE-R03 v2,
     SETTLE-R04, SETTLE-R06) is the legacy behaviour. A cap would reject
     requests the legacy application accepts, which is a behaviour change
     and by decision 7 needs a change record and a new rule version, not a
     silent tightening in the port. The accepted syntax is pinned by
     `LegacyCoercionsTest` and recorded as QUIRK-08.
9. `settlement_id` allocation stays `max(settlement_id) + 1`: the legacy
   schema has no sequence or identity column, and this service is the only
   writer to `SETTLEMENT`. Allocation and insert run under one lock in
   `SettlementRepository.save`; `SettlementRepositoryTest` runs 32 saves at
   once and expects 32 distinct ids.
10. Arithmetic is `double` end to end (`Double.parseDouble`, the legacy
    calculator statements, `String.format("%.2f")`), not `BigDecimal`. The
    first draft used `BigDecimal` with `HALF_UP` and would have settled
    `1.005` at `1.01`; see QUIRK-01 and QUIRK-08.

## Consequences

* The new service must reproduce fallbacks that look like bugs (SETTLE-R02,
  R03, R05) until an SME answers the open questions.
* Rounding must match `double` arithmetic, not `BigDecimal` defaults; see
  QUIRK-01 and QUIRK-08 in the quirks register.
* The in-memory HSQLDB and the relative `legacy-root` path (decision 4) make
  the service a parity target, not a deployable: saved settlements vanish on
  restart and the service must start from `services/settlement-service` (as
  `make service-run` does) or be given `LEGACY_ROOT`. A durable datasource
  and authentication (OQ-04) are deployment decisions outside this record.
* Operational failures (database down, missing claim on save) return the
  legacy error screen with HTTP 200 (QUIRK-05, OQ-02). Anything that
  monitors the service must read `screen`, not the status code, until OQ-02
  is answered.
* Characterisation tests against the legacy calculator land in their own pull
  request before the service, so a failing parity run points at the new code.
