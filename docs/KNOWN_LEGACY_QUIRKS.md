# Known legacy quirks reproduced in `services/settlement-service`

Every behaviour below was observed in, or inferred from, the running NorthStar
Claims monolith (see `docs/specs/SPEC-SETTLE-001.md` for the evidence column) and
is reproduced **deliberately** by the extracted settlement service so that the
golden transcripts under `transcripts/` replay with identical business outcomes.

Reproduction is a note, not an endorsement. Each entry is a candidate for a
separate, explicit business decision; none may be "fixed" as a side effect of
migration, because the transcripts are the contract and would fail.

Legend: **Reproduced** = the service behaves exactly like the monolith.
**Changed** = the service deliberately differs; listed here so nothing is silent.

## Input coercion (what Struts and `ClaimsActionSupport` did silently)

| Rule | Quirk | Where in the service | Test |
|------|-------|----------------------|------|
| SETTLE-R04 | The three mappings have `validate="false"` and `SettlementForm.validate` returns nothing, so **no input ever produces a validation error**. `validationErrors` is always `[]`. | `api/ScreenResponse.of` | `SettlementControllerTest.settleR04R05NoParametersNoValidationErrors` |
| SETTLE-R05 | The actions read `request.getParameter` directly; the form bean is never consulted. Every parameter is optional and bound as a raw string, never typed by the framework. | `api/SettlementController` (`@RequestParam(required = false) String`) | same |
| SETTLE-R06 | A submitted `policyLimit` is **ignored**; the limit always comes from the claim's policy (or R14). | `domain/SettlementRequest` (field kept, never read for the limit) | `LegacyCoercionsTest.settleR06PolicyLimitIgnored` |
| SETTLE-R11 | Missing or unparseable `claimId` silently becomes **claim 119** on calculate, save and detail. | `domain/LegacyCoercions.DEFAULT_CLAIM_ID` | `LegacyCoercionsTest.settleR11...`, `SettlementServiceTest`, `SettlementControllerTest.settleR11...` |
| SETTLE-R12 | Missing or unparseable `coveredAmount` silently becomes **5000**. | `LegacyCoercions.DEFAULT_COVERED_AMOUNT` | `LegacyCoercionsTest.settleR12...` |
| SETTLE-R13 | Missing or unparseable `depreciation` silently becomes **0**. | `LegacyCoercions.DEFAULT_DEPRECIATION` | `LegacyCoercionsTest.settleR13...` |
| SETTLE-R14 | On calculate, an unknown claim or policy silently uses a **policy limit of 10000**. | `LegacyCoercions.FALLBACK_POLICY_LIMIT`, `SettlementService.calculate` | `SettlementServiceTest.settleR14...` |
| SETTLE-R15 | On save, an unknown claim or policy does **not** fall back: the monolith threw `NullPointerException` into the global error page. The service throws `ClaimNotFoundException`, which the error handler maps to the same HTTP 200 + `error.jsp` outcome. | `SettlementService.save`, `api/LegacyErrorHandler` | `SettlementServiceTest.settleR15SaveFailsForMissingClaim`, `SettlementControllerTest.settleR15SaveUnknownClaimIsErrorScreenWith200` |
| SETTLE-R15 / R39 | The `<global-exceptions>` mapping also swallowed `SQLException`s from the DAOs: a database failure on save (e.g. a duplicate id from the unlocked `max+1` allocation) rendered `error.jsp` with **HTTP 200**, not a 500. The service maps Spring `DataAccessException` the same way, for the settlement controller only, without echoing SQL. | `api/LegacyErrorHandler.dataAccess` | `SettlementControllerTest.settleR15R39DataAccessFailureIsErrorScreenWith200` |
| SETTLE-R16 | Absent or empty-string `deductible` is **0.00**. | `LegacyCoercions.deductible`, `SettlementCalculator` | `SettlementCalculatorTest.settleR16...` |
| SETTLE-R17 | Whitespace-only `deductible` passes the action's `length() == 0` check untouched and is trimmed to 0.00 by the calculator. | `LegacyCoercions.deductible` (pass-through), `SettlementCalculator` (`trim`) | `SettlementCalculatorTest.settleR17...`, `LegacyCoercionsTest` |
| SETTLE-R18 | A non-blank, non-numeric `deductible` (`abc`) has **no fallback**: `Double.parseDouble` throws and the global exception handler renders `error.jsp` with **HTTP 200**, not 4xx/5xx. | `SettlementCalculator`, `LegacyErrorHandler.numberFormat` | `SettlementCalculatorTest.settleR18...`, `SettlementControllerTest.settleR18...` |
| SETTLE-R19 | **No range or sign checks** on any input; negative or absurd amounts are calculated as submitted. | no validation anywhere in `domain/` | `SettlementCalculatorTest.settleR19...` |
| SETTLE-R20 / R40 | Operator identity is the Struts session `user` (transcripts: `supervisor`). The service has no session; the operator is the configured `settlement.operator` (default `supervisor`) and is echoed as `savedBy`. | `SettlementService.operator`, `application.yml` | `SettlementControllerTest.settleR02R08R20SaveScreen`, `SettlementServiceTest.settleR40R48CalculatedByIsOperator` |

## Arithmetic (binary `double`, exactly as `SettlementCalculator`)

| Rule | Quirk | Where | Test |
|------|-------|-------|------|
| SETTLE-R21 / R22 | `gross = covered - depreciation`; `afterDeductible = gross - deductible`, all in `double`. | `domain/SettlementCalculator` | `SettlementCalculatorTest.settleR21...`, `settleR22...` |
| SETTLE-R23 | Amount after deductible is **floored at 0.00**; never negative. | same | `settleR23...` |
| SETTLE-R24 / R25 / R26 | Cap uses **strict `>`**: an amount exactly equal to the limit is *not* capped; floored 0.00 is not capped. | same | `settleR24...`, `settleR25...`, `settleR26...` |
| SETTLE-R27 | A policy limit of 0 forces any positive amount to 0.00 with `cappedAtLimit=true`. | same | `settleR27...` |
| SETTLE-R28 | Rounding is `Math.round(amount * 100.0) / 100.0` in binary double. **1.005 pays 1.00, not 1.01** (the value is a hair below 1.005 in IEEE-754). `BigDecimal.HALF_UP` would pay 1.01 and break `settlement_half_cent`. | same | `settleR28...` |
| SETTLE-R29 | Display and calculation round differently: the same request shows `coveredAmount 1.01` (`String.format("%.2f")` on the input) and `settlementAmount 1.00`. | `domain/LegacyDisplay.money` + calculator | `settleR29...`, `LegacyDisplayTest.settleR29...` |
| SETTLE-R30 | `deductibleApplied` reports the **full** parsed deductible even when it only partly (or not at all) reduced the settlement (`2000.00` with `settlementAmount 0.00`). | calculator | `settleR30...` |
| SETTLE-R31 | `coveredAmount` and `depreciation` are echoed as the raw parsed doubles, unrounded. | calculator | `settleR31...` |
| SETTLE-R32 | Exactly **one** rounding, after floor and cap; no intermediate rounding. | calculator | `settleR32...` |
| SETTLE-R33 | A deductible or depreciation larger than the policy limit is not an error. | calculator | `settleR33...` |

## Presentation (what `ns:field` rendered into the `f_*` spans)

| Rule | Quirk | Where | Test |
|------|-------|-------|------|
| SETTLE-R34 | Money is `String.format("%.2f", value)` in the root locale (`5000.00`, `0.00`). | `LegacyDisplay.money` | `LegacyDisplayTest.settleR34...` |
| SETTLE-R35 | `cappedAtLimit` is declared `type="money"` in `calculate.jsp` but, because `Double.parseDouble("true")` fails, `FieldTag` emits the literal `true`/`false`. The service emits the literal string directly. | `LegacyDisplay.flag` | `LegacyDisplayTest.settleR35...` |
| SETTLE-R07 / R08 | The calculate screen exposes exactly five fields; the save screen exactly two (`settlementAmount`, `savedBy`), and `savedBy` is the session user, **not** a value read back from the row. | `SettlementController.calculateFields`, `save` | `SettlementControllerTest.settleR01R07...`, `settleR02R08R20...` |
| SETTLE-R36 | The `policyLimit` request attribute is computed but never displayed. The service keeps it out of `fields` and exposes it only in the typed `data` payload. | `SettlementController.calculate` | `SettlementControllerTest.settleR01R07...` (`$.data.policyLimit`) |

## Persistence

| Rule | Quirk | Where | Test |
|------|-------|-------|------|
| SETTLE-R37 | Save **recomputes** from the request parameters; it never reuses a displayed result. | `SettlementService.save` | `SettlementServiceTest.settleR37R42SaveRecomputesAndStoresCalculatorOutput` |
| SETTLE-R38 | Every save **inserts** a new row; nothing is ever updated or deleted. | `persistence/SettlementRepository.insert` | `settleR38R39SaveAllocatesNextIdAndInserts` |
| SETTLE-R39 | New id is `max(settlement_id) + 1` over the whole table, read in a separate statement with no lock (race-prone, faithfully so). With the seeded rows 119 and 120 the first saved id is 121. | `SettlementRepository.nextId` | `settleR38R39SaveAllocatesNextIdAndInserts` |
| SETTLE-R41 | `calculated_date` is the **fixed literal `2019-04-01`**, whatever the real date. | `SettlementService.LEGACY_CALCULATED_DATE` | `settleR41CalculatedDateIsFixed` |
| SETTLE-R42 | Stored money columns are the calculator's raw doubles (`DOUBLE PRECISION`, not `NUMERIC`), so read-back equals what was computed. | `V1__settlement_schema.sql`, `SettlementRepository` | `settleR37R42SaveRecomputesAndStoresCalculatorOutput` |
| SETTLE-R43 / R44 | Probes: `settlement.claim.<id>.amount` in the legacy capture tool is a **recalculation** via `calculate.do?claimId=` with the R12/R13/R16 defaults, not a read of the saved row; the service therefore also answers `GET /api/settlement/calculate`. Calculating and saving never touch the `claim` row. | `SettlementController.calculateByGet`, `parity/routes.yaml` probes | `SettlementControllerTest.settleR01CalculateAnswersGet`, `settleR44ClaimProbe` |
| SETTLE-R45 | Detail shows the row with the **highest `settlement_id`** for the claim (`order by settlement_id desc limit 1`), which equals "most recent" only while ids are allocated monotonically. | `SettlementRepository.findLatestByClaim` | `SettlementServiceTest.settleR03R45R46DetailReturnsLatestOrEmpty` |
| SETTLE-R48 | `calculated_by` is `String.valueOf(user)`, so a missing operator persists the literal string `"null"`. Reproduced via `String.valueOf(operator)`. | `SettlementService.save` | `SettlementServiceTest.settleR40R48CalculatedByIsOperator` |

## Deliberately changed behaviour (listed so nothing is silent)

| Rule | Legacy behaviour | Service behaviour | Why |
|------|------------------|-------------------|-----|
| SETTLE-R46 | Detail for a claim with no saved settlement forwards a `null` bean and `detail.jsp` fails on `<bean:write name="settlement">` (an HTTP 500 page from the JSP engine, not the Struts error forward). | `GET /api/settlement/detail` returns **HTTP 404** with an empty body. | There is no JSP to crash; no transcript covers this path; a 404 is the honest JSON equivalent of "no such settlement". Flagged for review. |
| SETTLE-R09 / R10 | `calculate.jsp` renders a detail link with an empty `claimId` and a save link with no parameters, so following the links operates on the R11-R13 fallback values rather than the figures displayed. | The service renders no links; callers pass parameters explicitly. | Hyperlinks are JSP plumbing, not business logic. The underlying fallbacks (R11-R13, R17) are reproduced, so a caller that omits parameters still gets the legacy outcome. |
| SETTLE-R47 | `com.northstar.claims.service.SettlementService.calculateAndSave` exists with different defaults (`id = claimId + 10000`, date `2019-03-01`) but is dead code: no action calls it. | Not ported. | Dead code is reported, not migrated. Deleting it from the monolith is a separate decision. |
| (sanctioned) | `SettlementDAO` and `PolicyDAO` used JDBC `PreparedStatement` parameters already; the extracted repositories use `JdbcClient` named parameters throughout. | Parameterised SQL only. | The one sanctioned change class; observably identical and proven by the transcripts. |
| (structural) | HTML screens rendered by JSP. | JSON `ScreenResponse { legacyForward, fields, validationErrors, data }`. | `fields` carries the same `f_*` values the JSP emitted, formatted identically, so the replay compares business outcomes rather than markup. |

## Not extracted, filed under module `settlement` in `transcripts/index.json`

`payment_issue` (`POST /claims/payment/issue.do`) and `payment_history`
(`GET /claims/payment/history.do`) are recorded under module `settlement` but are
served by `IssuePaymentAction`, `PaymentHistoryAction` and `PaymentDAO`, which are
outside the seam defined by SPEC-SETTLE-001. The service does not implement them;
`parity/replay.py` reports them as `SKIP (route not extracted)` and never counts
them as passing. They stay with the monolith until a payments extraction.
