## Summary

Extracts the settlement module of the Struts monolith into `services/settlement-service` (Spring Boot 3, Java 21, port 8083, own PostgreSQL schema via Flyway), behind a replay harness that proves it against the six settlement transcripts. Nothing under `src/`, `transcripts/`, `pom.xml` or `build.xml` is touched; the monolith keeps working unchanged.

The non-obvious parts:

**The service speaks the legacy screens, not JSON.** Parity is only mechanical if the oracle parser works on both sides, so `ScreenRenderer` emits the same markers the capture harness greps for — `<!-- ns:view PATH -->`, `<span id="f_NAME">VALUE</span>`, `<!-- ns:error KEY -->` — which lets `parity/replay.py` compare status, declared forward, business fields and validation keys with one regex set.

**The arithmetic is deliberately the legacy binary-double arithmetic**, not `BigDecimal` (SETTLE-R21..R34):

```java
double after = (coveredAmount - depreciation) - deductible;
if (after < 0) after = 0;                  // R23 floor, but the full deductible is still echoed (R30)
boolean capped = after > policyLimit;      // strict >, so limit-equal is not "capped" (R24/R25)
return Math.round((capped ? policyLimit : after) * 100.0) / 100.0;  // R28/R29 half-cent behaviour
```

Switching to decimal arithmetic changes `settlement_half_cent`, so OQ-05 is resolved as "keep the defect".

**Invalid deductible follows CHG-001 on `calculate` only.** `calculate` returns HTTP 200 + calculate forward + `settlement.deductible.invalid` with empty fields (SETTLE-R18 v2); `save` still falls through `LegacyFailureAdvice` to HTTP 200 + `/WEB-INF/jsp/error.jsp`, because OQ-15(a) leaves the save route undecided. Same reason `calculate` keeps the 10000.00 fallback limit for an unknown claim (R49) while `save` fails (R15). `detail` for a claim with no settlement also reaches the error page, because `detail.jsp` line 24 reads `calculatedBy` off the missing bean (SETTLE-R46). All three routes accept GET as well as POST, like the Struts mappings.

Persistence mirrors the DAO: insert-only, id = `max(settlement_id) + 1` (R38/R39/R42), operator `supervisor` and date `2019-04-01` fixed (R20/R40/R41), detail reads the highest settlement id for the claim (R45). Flyway `V1`/`V2` create the service-owned tables and seed policies 9001/9002 and claims 119/120 with the `DatabaseBootstrap` values.

`docker-compose.yml` is parameterised by `NS` (compose project name) and `PORT_OFFSET`, so `NS=b PORT_OFFSET=100 make up` runs a second stack on 8183/5532 beside the first; both stacks were brought up simultaneously and replayed green. Makefile gains `up`, `down`, `parity`, `service-build`, `service-test`, `service-lint`, `sast`; `build`, `run`, `seed`, `test`, `capture`, `clean` are byte-for-byte unchanged.

## Verification

| Command | Exit |
| --- | --- |
| `make service-build` | 0 |
| `make service-test` (25 tests, all SETTLE-R named) | 0 |
| `make service-lint` (checkstyle) | 0 |
| `make sast` (spotbugs + findsecbugs) | 0 |
| `make up` | 0 |
| `make parity` | 0 — 6 PASS, 0 FAIL, 16 SKIP |
| `NS=b PORT_OFFSET=100 make up` | 0 |
| `NS=b PORT_OFFSET=100 make parity` | 0 — 6 PASS, 0 FAIL, 16 SKIP |
| `make down` / `NS=b PORT_OFFSET=100 make down` | 0 |
| `git status --porcelain -- src transcripts pom.xml build.xml` | empty |

Parity output (`parity/report.md`, gitignored as generated):

```
PASS settlement_calculate: status 200, forward and 5 business fields match
PASS settlement_save: status 200, forward and 2 business fields match
PASS settlement_blank_deductible: status 200, forward and 5 business fields match
PASS settlement_half_cent: status 200, forward and 5 business fields match
PASS settlement_policy_cap: status 200, forward and 5 business fields match
PASS settlement_deductible_floor: status 200, forward and 5 business fields match
16 non-settlement scenarios SKIP (out of scope per SPEC-SETTLE-001 section 2.1)
```

SpotBugs exclusions are limited to `SPRING_ENDPOINT`, `EI_EXPOSE_REP2` on constructor-injected `JdbcTemplate` beans, `SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING` (the legacy mappings accept GET; authentication and CSRF live in the monolith front door) and `POTENTIAL_XML_INJECTION` in `ScreenRenderer`, whose inputs all pass through `escape` first.

## Known gaps, deliberately not closed here

- The service has no authentication and records the operator as the constant `supervisor`; the legacy action takes it from the session (SETTLE-R40/R48). Both close when the monolith front door routes `/settlement/*` here and propagates a trusted identity.
- `max(settlement_id) + 1` is racy, exactly as the legacy DAO is; changing it would change observed ids.
- The service database is separate from the monolith's HSQLDB, as specified, so this stack is a parity harness rather than a cutover.
- Parity compares the response surface only; transcript `db_state` probes are the next increment.
