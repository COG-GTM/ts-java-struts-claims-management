# Struts to Spring mapping: settlement module

Source of truth: the running NorthStar Claims monolith and its golden transcripts
(`transcripts/`). Rule ids refer to `docs/specs/SPEC-SETTLE-001.md`. The
reproduced quirks are catalogued in `docs/KNOWN_LEGACY_QUIRKS.md`.

## Seam

| Legacy artefact (unchanged, under `src/`) | Role | Spring replacement (`services/settlement-service`) |
|---|---|---|
| `struts-config.xml` `/settlement/calculate`, `/settlement/save`, `/settlement/detail` mappings | routing, forwards, `validate="false"` | `api/SettlementController` `@RequestMapping("/api/settlement")` |
| `web.SettlementCalculateAction` | inbound contract for calculate | `SettlementController.calculate` (`POST /api/settlement/calculate`) and `calculateByGet` |
| `web.SettlementSaveAction` | inbound contract for save | `SettlementController.save` (`POST /api/settlement/save`) |
| `web.SettlementDetailAction` | inbound contract for detail | `SettlementController.detail` (`GET /api/settlement/detail`) |
| `web.form.SettlementForm` | String-typed form bean, never read (R05) | `domain/SettlementRequest` (raw strings + resolved accessors) |
| `web.ClaimsActionSupport.integer/decimal` | silent numeric fallbacks (R11-R13) | `domain/LegacyCoercions` |
| `web.ClaimsActionSupport.nextId` | `max(id)+1` allocator (R39) | `persistence/SettlementRepository.nextId` |
| `service.SettlementCalculator` | the domain rule (R21-R33) | `domain/SettlementCalculator`, line for line |
| `service.SettlementService` | dead code (R47) | not ported |
| `dao.SettlementDAO` | JDBC persistence | `persistence/SettlementRepository` (`JdbcClient`, named parameters) |
| `dao.PolicyDAO.findById` | policy limit lookup (R14) | `persistence/PolicyRepository.findLimit` |
| `dao.ClaimDAO.findById` (only `policy_id`, `status` are used) | claim lookup | `persistence/ClaimRepository.findById` |
| `jsp/settlement/calculate.jsp`, `save.jsp`, `detail.jsp` + `tag.FieldTag` | rendering of `f_*` spans (R07, R08, R34, R35) | `api/ScreenResponse` + `domain/LegacyDisplay` |
| `<global-exceptions>` -> `error.jsp` | error rendering with HTTP 200 (R15, R18 on save) | `api/LegacyErrorHandler` |
| (none: `validate="false"`, R04) | CHG-001 deductible check on calculate (R18 v2): `settlement.deductible.invalid`, calculate screen redisplayed, no result | `domain/LegacyCoercions.isInvalidDeductible`, `api/SettlementController.calculate`, `api/ScreenResponse.invalid` |
| HSQLDB `SETTLEMENT`, `CLAIM`, `POLICY` tables + `DatabaseBootstrap` seed | schema and fixture | Flyway `V1__settlement_schema.sql`, `V2__settlement_seed.sql` (PostgreSQL) |
| session attribute `user` | operator identity (R20, R40, R48) | `settlement.operator` property (`SETTLEMENT_OPERATOR`, default `supervisor`) |

## Classification of every method reached (playbook step 2)

**(a) Domain rules, migrated as logic**

* `SettlementCalculator.calculate(double, String, double, double)`: gross loss,
  deductible parsing and flooring, strict cap, single `Math.round` (R16-R18,
  R21-R33). Ported verbatim into `domain/SettlementCalculator`; on the calculate
  route the R18 parse failure is pre-empted by the CHG-001 check (R18 v2).
* `ClaimsActionSupport.integer` / `decimal` fallbacks and their constants
  119 / 5000 / 0 / 10000 (R11-R14). These *look* like plumbing but change the
  business outcome of a blank submission, so they are domain rules and live in
  `domain/LegacyCoercions`.
* `SettlementSaveAction` lines 34-38: id allocation, operator stamp, fixed date
  `2019-04-01` (R39-R41, R48). In `domain/SettlementService.save`.
* `SettlementDetailAction` + `SettlementDAO.findLatestByClaim`: highest id wins
  (R45). In `domain/SettlementService.detail`.

**(b) Struts/JSP plumbing, replaced by the framework**

* `ActionForm` population of `SettlementForm` (never read, R05).
* `ActionForward` selection (`success` -> JSP) becomes `ScreenResponse.legacyForward`.
* `ns:field` taglib formatting: the *values* are business fields the transcripts
  compare, so the formatting rules (R34, R35) are kept in `LegacyDisplay`; the
  HTML around them is dropped.
* The links on `calculate.jsp` (R09, R10): dropped, see KNOWN_LEGACY_QUIRKS.
* `ActionMessages`: never populated (R04); `validationErrors` is `[]` except for
  the CHG-001 calculate check (R18 v2).

**(c) Persistence, rewritten against `JdbcClient`**

* `SettlementDAO.insert`, `findLatestByClaim`, `PolicyDAO.findById`,
  `ClaimDAO.findById`. All parameterised. The legacy DAOs already used
  `PreparedStatement` parameters; no string-concatenated SQL existed in the seam.

**Unclassifiable at first sight (resolved)**

* `String.valueOf(user)` for `calculated_by` (R48): looked like plumbing, is a
  persistence rule (`"null"` is stored). Reproduced.
* Probe `settlement.claim.<id>.amount` in `tools/capture/capture.py` resolves via
  `GET /settlement/calculate.do?claimId=`: a recalculation, not a read. The
  service therefore answers GET on calculate (R01) so the probe means the same.

## Inbound contract

| Legacy | Service | Parameters (all optional raw strings) |
|---|---|---|
| `POST /claims/settlement/calculate.do` | `POST /api/settlement/calculate` (also `GET`) | `claimId`, `coveredAmount`, `deductible`, `depreciation`, `policyLimit` (ignored, R06) |
| `POST /claims/settlement/save.do` | `POST /api/settlement/save` | same |
| `GET /claims/settlement/detail.do?claimId=` | `GET /api/settlement/detail?claimId=` | `claimId` |
| (probe support) | `GET /api/settlement/claims/{claimId}` | path variable |

Form encoding is `application/x-www-form-urlencoded`, exactly as the browser
posted to Struts; `parity/routes.yaml` forwards the recorded form unchanged.

## Outbound contract

```json
{
  "legacyForward": "/WEB-INF/jsp/settlement/calculate.jsp",
  "fields": {
    "coveredAmount": "5000.00",
    "deductibleApplied": "500.00",
    "depreciation": "0.00",
    "cappedAtLimit": "true",
    "settlementAmount": "1000.00"
  },
  "validationErrors": [],
  "data": { "claimId": 119, "policyLimit": 1000.0, "result": { "...": "typed doubles" } }
}
```

* `legacyForward` is compared with the transcript `result` line
  (`forward:<jsp>`; `error.jsp` maps to `error:errors.system`).
* `fields` are the `<span id="f_NAME">` values, formatted by `LegacyDisplay`,
  compared key for key with `business_fields`.
* `validationErrors` is compared with `validation_errors` (`[]` in every recorded
  transcript, R04; `["settlement.deductible.invalid"]` only for the CHG-001
  calculate case, R18 v2, which no transcript records).
* `data` is the typed payload for new consumers and is **not** part of parity.
* Save: `fields = { settlementAmount, savedBy }` (R08); detail: the eight
  `detail*` spans of `detail.jsp`; error: HTTP 200 with
  `legacyForward = /WEB-INF/jsp/error.jsp` (R15, R18 on save).

## Database

PostgreSQL schema owned by the service, managed by Flyway:

* `V1__settlement_schema.sql`: `policy`, `claim`, `settlement` with the legacy
  column names; money columns are `DOUBLE PRECISION` so stored values are the
  calculator's binary doubles (R42).
* `V2__settlement_seed.sql`: the rows the six transcripts touch, copied from
  `DatabaseBootstrap` / `src/main/resources/db/seed.sql`: policy 9001 (limit
  1000, the cap trap) and 9002 (limit 100000), claim 119 (`DENIED`, policy 9001)
  and 120 (`CLOSED`, policy 9002), and seed settlement rows 119 and 120 so that
  the first save allocates id 121 exactly as the monolith does (R39).

## Running

```
make service-build | service-test | service-lint | sast    # Maven, Java 21
make up   NS=settlement PORT_OFFSET=100                     # postgres + service on 8183/5533
make parity NS=settlement PORT_OFFSET=100 MODULE=settlement # writes parity/report.md
make down NS=settlement PORT_OFFSET=100
```

`docker-compose.yml` reads `NS` (compose project, container, volume and image
tag), `SERVICE_HOST_PORT`/`POSTGRES_HOST_PORT` (derived by the Makefile from
`PORT_OFFSET`) and `MAVEN_MIRROR` (passed to the image build when Maven Central is
unreachable, e.g. `https://maven-central.storage-download.googleapis.com/maven2`).
