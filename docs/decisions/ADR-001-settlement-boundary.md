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

## Consequences

* The new service must reproduce fallbacks that look like bugs (SETTLE-R02,
  R03, R05) until an SME answers the open questions.
* Rounding must match `double` arithmetic, not `BigDecimal` defaults; see
  the quirks register once parity has run.
* Characterisation tests against the legacy calculator land in their own pull
  request before the service, so a failing parity run points at the new code.
