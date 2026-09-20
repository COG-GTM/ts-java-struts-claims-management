# SPEC-SETTLE-001: Settlement module behavioural specification

| Field    | Value                                                                 |
|----------|-----------------------------------------------------------------------|
| ID       | SPEC-SETTLE-001                                                       |
| Version  | 0.3                                                                   |
| Status   | Draft, derived from the transcripts, execution evidence and source files in section 2 only; each rule's status says which (see 2.3 and 2.4) |
| Module   | Settlement (`/settlement/calculate`, `/settlement/save`, `/settlement/detail`) |
| Baseline | `main` at commit `225c8d3`                                            |

## 1. Purpose and scope

This document records what the NorthStar settlement module *does today*. It is
not a statement of what the module *should* do. Every rule is derived from one
of three kinds of evidence and nothing else:

1. The six settlement transcripts under `transcripts/` (see section 2), which
   are deterministic captures of real requests against the seeded application.
2. Execution evidence (`E:` citations, section 2.3): a request made by a
   reviewer against the running legacy application, recorded with the exact
   request and the observed response.
3. The source files listed in section 2.

Where the code implies a behaviour that no transcript or execution exercises,
the rule is recorded but flagged as such. Where a behaviour looks accidental
and cannot be adopted as a requirement without a decision from the business,
the rule keeps its evidence status and the decision is recorded in section 5.

The one exception is a **versioned rule**: when a business decision changes a
rule, the current-state rule keeps its id and text, and the approved future
state is added directly beneath it with the same id and a version suffix
(`SETTLE-R18 v2`), a reference to the change record under `docs/changes/`,
and status `Open` until the code and a transcript exist. The unversioned rule
remains the description of what the module does today.

Out of scope: payment issue and payment history (`/payment/*`), authentication
(`AuthFilter`), the workbench and reporting screens, and the HSQLDB schema.

## 2. Sources

### 2.1 Transcripts (observed behaviour)

| Scenario                        | File                                          | Request                                                                                  |
|---------------------------------|-----------------------------------------------|------------------------------------------------------------------------------------------|
| `settlement_calculate`          | `transcripts/settlement_calculate.json`       | POST `/claims/settlement/calculate.do` claimId=119, covered=5000.00, deductible=500.00, depreciation=0.00 |
| `settlement_save`               | `transcripts/settlement_save.json`            | POST `/claims/settlement/save.do` claimId=119, covered=5000.00, deductible=500.00, depreciation=0.00      |
| `settlement_blank_deductible`   | `transcripts/settlement_blank_deductible.json`| POST `/claims/settlement/calculate.do` claimId=120, covered=5000.00, deductible="", depreciation=500.00   |
| `settlement_half_cent`          | `transcripts/settlement_half_cent.json`       | POST `/claims/settlement/calculate.do` claimId=120, covered=1.005, deductible="", depreciation=0.00       |
| `settlement_policy_cap`         | `transcripts/settlement_policy_cap.json`      | POST `/claims/settlement/calculate.do` claimId=119, covered=20000.00, deductible=100.00, depreciation=0.00|
| `settlement_deductible_floor`   | `transcripts/settlement_deductible_floor.json`| POST `/claims/settlement/calculate.do` claimId=120, covered=1000.00, deductible=2000.00, depreciation=0.00|

All six run as actor `supervisor`, expect HTTP 200, and expect an empty
`validation_errors` list. `transcripts/README.md` defines the transcript format
and the `db_state` probe vocabulary.

### 2.2 Source files (inferred behaviour)

| Short name            | Path                                                                        |
|-----------------------|-----------------------------------------------------------------------------|
| `SCA`                 | `src/main/java/com/northstar/claims/web/SettlementCalculateAction.java`     |
| `SSA`                 | `src/main/java/com/northstar/claims/web/SettlementSaveAction.java`          |
| `SDA`                 | `src/main/java/com/northstar/claims/web/SettlementDetailAction.java`        |
| `SettlementForm`      | `src/main/java/com/northstar/claims/web/form/SettlementForm.java`           |
| `CAS`                 | `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java`           |
| `Calculator`          | `src/main/java/com/northstar/claims/service/SettlementCalculator.java`      |
| `SettlementService`   | `src/main/java/com/northstar/claims/service/SettlementService.java`         |
| `SettlementDAO`       | `src/main/java/com/northstar/claims/dao/SettlementDAO.java`                 |
| `PolicyDAO`           | `src/main/java/com/northstar/claims/dao/PolicyDAO.java`                     |
| `struts-config`       | `src/main/webapp/WEB-INF/struts-config.xml` (the three `/settlement/*` mappings) |
| `calculate.jsp`       | `src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp`                      |
| `save.jsp`            | `src/main/webapp/WEB-INF/jsp/settlement/save.jsp`                           |
| `detail.jsp`          | `src/main/webapp/WEB-INF/jsp/settlement/detail.jsp`                         |

Line numbers below refer to these files at the baseline commit.

### 2.3 Citation kinds

| Kind                 | Form                                                        | Meaning                                                                                    |
|----------------------|-------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Transcript scenario  | `` `settlement_calculate` `` (a scenario name from 2.1)      | The behaviour is asserted by that transcript's `result`, `status`, `business_fields` or `db_state`. |
| Source line          | `` `SCA` 23 `` (a short name from 2.2 and line numbers)     | The behaviour is read from that source location at the baseline commit.                    |
| Execution evidence   | `E:` followed by actor, method, path and the exact parameters | The behaviour was reproduced by a reviewer against the running legacy application; the response status and forward are recorded in the rule. |

### 2.4 Status legend

The status column uses exactly three markers.

| Status     | Meaning                                                                                                   |
|------------|-----------------------------------------------------------------------------------------------------------|
| `Observed` | The behaviour is exercised by at least one transcript in section 2.1 or by execution evidence (`E:`).     |
| `Inferred` | The behaviour is read from code only; no transcript or execution exercises it.                            |
| `Open`     | The behaviour has neither transcript, execution nor source evidence: it records a pending business decision, or an approved future-state behaviour (a versioned rule citing its `CHG-` record, section 1) that is not yet implemented. |

Pending business decisions are not carried in the status column; they are
recorded in section 5 and cross-referenced from the rule. At version 0.3 the
only rule carrying `Open` is SETTLE-R18 v2 (approved by CHG-001, not yet
implemented).

## 3. Behavioural rules

### 3.1 Routing and screens

| ID         | Rule                                                                                                                                                       | Evidence                                                                                         | Status   |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|----------|
| SETTLE-R01 | A POST to `/settlement/calculate.do` computes a settlement from the request parameters and forwards to `/WEB-INF/jsp/settlement/calculate.jsp` with HTTP 200. | All five `calculate` scenarios (`result`, `status`); `struts-config` 205-213; `SCA` 43            | Observed |
| SETTLE-R02 | A POST to `/settlement/save.do` recomputes the settlement from the request parameters and forwards to `/WEB-INF/jsp/settlement/save.jsp` with HTTP 200 (the insert itself is source-derived, see R38 and OQ-12). | `settlement_save` (`result`, `status`); `struts-config` 214-222; `SSA` 30-45                     | Observed |
| SETTLE-R03 | A request to `/settlement/detail.do` loads the highest-id settlement for the claim (see R45) and forwards to `/WEB-INF/jsp/settlement/detail.jsp`.        | `struts-config` 356-363; `SDA` 17-23; `SettlementDAO` 100-115                                     | Inferred |
| SETTLE-R04 | The settlement actions never produce Struts validation errors because all three mappings declare `validate="false"` and `SettlementForm.validate` returns an empty `ActionErrors`. | All six scenarios (`validation_errors: []`); `struts-config` 210, 219, 359; `SettlementForm` 73-75 | Observed |
| SETTLE-R05 | The `settlementForm` bean is bound to `/settlement/calculate` and `/settlement/save` in request scope but its properties are never read by the actions, which read `request.getParameter` directly. | `struts-config` 208-209, 217-218; `SettlementForm` 13-16; `SCA` 23, 32-35; `SSA` 24, 27-29         | Inferred |
| SETTLE-R06 | The `policyLimit` value submitted by the browser is ignored; the limit always comes from the claim's policy (or the fallback in SETTLE-R49).                  | `SettlementForm` 16, 53-61 (declared) versus `SCA` 25-31 and `SSA` 26, 33 (never read)             | Inferred |
| SETTLE-R07 | The calculate screen renders the five business fields `coveredAmount`, `deductibleApplied`, `depreciation`, `cappedAtLimit` and `settlementAmount` as `<span id="f_NAME">` elements. | All five `calculate` scenarios (`business_fields`); `calculate.jsp` 20-28                         | Observed |
| SETTLE-R08 | The save screen renders exactly two business fields, `settlementAmount` and `savedBy`, where `savedBy` is the session `user` attribute rather than a value read back from the saved row. | `settlement_save` (`business_fields`); `save.jsp` 19-22; `SSA` 41-42                              | Observed |
| SETTLE-R09 | The "Settlement detail" link on the calculate screen is rendered from a `claimId` request attribute that `SettlementCalculateAction` never sets, so the link is emitted as `detail.do?claimId=`. | `calculate.jsp` 17; `SCA` 38-42 (sets only `settlement`, `policyLimit`, `screenName`)             | Inferred |
| SETTLE-R10 | The "Save settlement" link on the calculate screen points at `save.do` with no parameters, so following it saves a settlement built entirely from the fallback values in SETTLE-R11, R12, R13 and R16 rather than the figures displayed (OQ-08). | `calculate.jsp` 30; `SSA` 24-33; `CAS` 31-45                                                      | Inferred |

### 3.2 Input handling

| ID         | Rule                                                                                                                                                       | Evidence                                                                                         | Status   |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|----------|
| SETTLE-R11 | `claimId` is parsed with `Integer.parseInt`, and a missing or unparseable value silently falls back to claim 119 on all three settlement actions (OQ-01).    | `SCA` 23; `SSA` 24; `SDA` 17; `CAS` 31-37                                                         | Inferred |
| SETTLE-R12 | `coveredAmount` is parsed with `Double.parseDouble`, and a missing or unparseable value silently falls back to 5000 (OQ-02).                                | `SCA` 32; `SSA` 27; `CAS` 39-45                                                                   | Inferred |
| SETTLE-R13 | `depreciation` is parsed with `Double.parseDouble`, and a missing or unparseable value silently falls back to 0.                                            | `SCA` 33; `SSA` 28; `CAS` 39-45                                                                   | Inferred |
| SETTLE-R14 | On `/settlement/calculate`, the policy limit is the `policy_limit` of the policy referenced by the claim (see R49 for the missing-policy case).                | `settlement_calculate`, `settlement_policy_cap` (claim 119 capped at 1000.00); `SCA` 25-31; `CAS` 93-100; `PolicyDAO` 26-44, 173 | Observed |
| SETTLE-R15 | On `/settlement/save`, a claim that cannot be found, or a claim whose policy cannot be found, causes a `NullPointerException` and the request fails rather than falling back to any limit. | `SSA` 25-26, 33 (no null checks)                                                                  | Inferred |
| SETTLE-R16 | A `deductible` parameter that is absent or an empty string is treated as a deductible of 0.00.                                                             | `settlement_blank_deductible`, `settlement_half_cent` (`deductibleApplied: 0.00`); `SCA` 34-38; `Calculator` 26-30 | Observed |
| SETTLE-R17 | A `deductible` consisting only of whitespace passes the action's empty check but is trimmed by the calculator and also treated as 0.00.                     | `SCA` 36-37 (`length() == 0` only); `Calculator` 28                                              | Inferred |
| SETTLE-R18 | A non-blank `deductible` that is not a number (for example `abc`) is parsed with `Double.parseDouble` and no fallback, and the resulting `NumberFormatException` is caught by the Struts global exception handler, so the request returns HTTP 200 with the forward `/WEB-INF/jsp/error.jsp` rather than an HTTP 500 or a redisplay of the calculate screen (OQ-04). | `E:` as `supervisor`, POST `/claims/settlement/calculate.do` `claimId=120&coveredAmount=1000&deductible=abc&depreciation=0` gives HTTP 200, forward `/WEB-INF/jsp/error.jsp`; `Calculator` 28-29; `struts-config` 59-60 (`global-exceptions` for `java.lang.Exception`); contrast with `CAS` 39-45 which is not used for the deductible | Observed |
| SETTLE-R18 v2 | **Approved future state (CHG-001), not yet implemented.** A non-blank `deductible` that is not a valid number is rejected before any calculation: no settlement is computed, and `/settlement/calculate.do` redisplays the calculate screen (`/WEB-INF/jsp/settlement/calculate.jsp`) with HTTP 200 and the validation key `settlement.deductible.invalid`; an absent, empty or whitespace-only deductible is still 0.00 (R16, R17). | `docs/changes/CHG-001-invalid-deductible.md` (owner Ben Lau, 2026-09-20); no transcript and no source line yet; supersedes SETTLE-R18 once implemented | Open     |
| SETTLE-R19 | No range or sign checks are applied to any settlement input; negative or very large amounts are calculated as submitted.                                    | `SCA` 23-38; `SSA` 24-33; `CAS` 328-330 (`financialAmount` exists but is not called)              | Inferred |
| SETTLE-R20 | Operator identity for the settlement screens is the session attribute `user`, which the transcripts show as `supervisor`.                                  | `settlement_save` (`savedBy: supervisor`); `SSA` 36-37; `save.jsp` 21-22                          | Observed |

### 3.3 Calculation

Let `covered`, `depreciation`, `deductible` and `limit` be the resolved inputs
after section 3.2. The calculator then applies the following rules in order.

| ID         | Rule                                                                                                                                                       | Evidence                                                                                         | Status   |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|----------|
| SETTLE-R21 | Gross loss is `covered - depreciation`.                                                                                                                     | `settlement_blank_deductible` (5000.00 - 500.00 = 4500.00); `Calculator` 31                       | Observed |
| SETTLE-R22 | The deductible is subtracted from the gross loss to give the amount after deductible.                                                                       | `settlement_calculate`, `settlement_policy_cap`, `settlement_deductible_floor`; `Calculator` 32    | Observed |
| SETTLE-R23 | When the deductible exceeds the gross loss the amount after deductible is floored at 0.00 and is never negative.                                            | `settlement_deductible_floor` (1000.00 - 2000.00 gives `settlementAmount: 0.00`); `Calculator` 33-34 | Observed |
| SETTLE-R24 | `cappedAtLimit` is true and the settlement is replaced by the policy limit when the amount after deductible is strictly greater than the policy limit.     | `settlement_calculate` (4500 > 1000 gives 1000.00, `cappedAtLimit: true`); `settlement_policy_cap` (19900 > 1000 gives 1000.00); `Calculator` 35-36 | Observed |
| SETTLE-R25 | An amount after deductible exactly equal to the policy limit is not treated as capped (`cappedAtLimit` is `false`).                                        | `Calculator` 35 (`>` not `>=`)                                                                    | Inferred |
| SETTLE-R26 | `cappedAtLimit` is `false` whenever the amount after deductible is at or below the policy limit, including when it has been floored to 0.00.               | `settlement_blank_deductible`, `settlement_half_cent`, `settlement_deductible_floor` (`cappedAtLimit: false`); `Calculator` 35 | Observed |
| SETTLE-R27 | A policy limit of 0 forces the settlement to 0.00 for any positive amount after deductible.                                                                | `Calculator` 35-36 (0 < amount so `capped` is true and `amount = 0`)                             | Inferred |
| SETTLE-R28 | The settlement amount is rounded to cents with `Math.round(amount * 100.0) / 100.0` in binary double arithmetic, so a covered amount of 1.005 with no deductible or depreciation yields 1.00, not 1.01. | `settlement_half_cent` (`coveredAmount: 1.01`, `settlementAmount: 1.00`); `Calculator` 37         | Observed |
| SETTLE-R29 | The display and the calculation round the same value differently: the input 1.005 is displayed as 1.01 by the money formatter while the settlement computed from it is 1.00 (OQ-05). | `settlement_half_cent` (the same request shows 1.01 for the input and 1.00 for the result); `Calculator` 37, 48-50 | Observed |
| SETTLE-R30 | `deductibleApplied` reports the full parsed deductible even when only part of it (or none of it) reduced the settlement.                                   | `settlement_deductible_floor` (`deductibleApplied: 2000.00` with `settlementAmount: 0.00`); `Calculator` 40 | Observed |
| SETTLE-R31 | `coveredAmount` and `depreciation` are echoed back as the raw parsed doubles, unrounded; only the settlement amount is rounded.                            | `settlement_half_cent` (input 1.005 echoed and displayed as 1.01); `Calculator` 39, 41            | Observed |
| SETTLE-R32 | The result is rounded only once, after capping and flooring; intermediate values are not rounded.                                                          | `Calculator` 30-37                                                                                | Inferred |
| SETTLE-R33 | Only the settlement amount is validated against the policy limit; a deductible or depreciation larger than the limit is not itself an error.               | `settlement_deductible_floor`; `Calculator` 26-46 (no checks on inputs)                           | Observed |

### 3.4 Presentation of amounts

| ID         | Rule                                                                                                                                                       | Evidence                                                                                         | Status   |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|----------|
| SETTLE-R34 | Money fields are displayed with exactly two decimal places (`5000.00`, `0.00`, `1.00`).                                                                    | All six scenarios (`business_fields`); `calculate.jsp` 20-28, `save.jsp` 19, `detail.jsp` 14-22 (`type="money"`) | Observed |
| SETTLE-R35 | `cappedAtLimit` is displayed as the literal strings `true` or `false` even though the calculate screen declares it with `type="money"`.                    | All five `calculate` scenarios (`cappedAtLimit: "true"`/`"false"`); `calculate.jsp` 26              | Observed |
| SETTLE-R36 | The `policyLimit` request attribute set by the calculate action is not displayed on the calculate screen.                                                  | `SCA` 41; `calculate.jsp` 18-29 (no reference to `policyLimit`)                                    | Inferred |

### 3.5 Persistence and detail

| ID         | Rule                                                                                                                                                       | Evidence                                                                                         | Status   |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|----------|
| SETTLE-R37 | Saving with the same inputs as calculate yields the same settlement amount (1000.00 for claim 119 with 5000.00 / 500.00 / 0.00); see R50 for how.                | `settlement_save` and `settlement_calculate` (`settlementAmount: 1000.00` in both)                 | Observed |
| SETTLE-R38 | Every save inserts a new `SETTLEMENT` row; existing settlements for the claim are never updated or deleted by the settlement screens.                        | `SettlementDAO` 117-142 (`insert` only); `SSA` 39                                                  | Inferred |
| SETTLE-R39 | The new settlement's id is `max(settlement_id) + 1` over the whole `SETTLEMENT` table, computed in a separate connection without locking.                    | `SSA` 34; `CAS` 61-75                                                                              | Inferred |
| SETTLE-R40 | The saved row records `calculated_by` as the session `user` attribute (the transcript shows only the screen's `savedBy`, R08, not the stored column).       | `SSA` 36-37                                                                                        | Inferred |
| SETTLE-R41 | The saved row records the fixed `calculated_date` `2019-04-01` regardless of the actual date.                                                              | `SSA` 38                                                                                           | Inferred |
| SETTLE-R42 | The saved row stores `covered_amount`, `deductible_applied`, `depreciation`, `capped_at_limit` and `settlement_amount` exactly as produced by the calculator. | `SettlementDAO` 128-136                                                                            | Inferred |
| SETTLE-R43 | After `settlement_save` for claim 119 the `settlement.claim.119.amount` probe reads `1000.00`.                                                              | `settlement_save` (`db_state`)                                                                     | Observed |
| SETTLE-R44 | Calculating a settlement leaves the `CLAIM` row unchanged (claim 120 still reads `CLOSED` afterwards).                                                       | `settlement_blank_deductible` (`db_state` probe `claim.120.status` = `CLOSED`)                     | Observed |
| SETTLE-R45 | The detail screen shows the settlement with the highest `settlement_id` for the claim, which under the save action's `max + 1` allocation (R39) is the most recent save, although neither the DAO nor the schema enforces that ordering. | `SettlementDAO` 100-109 (`order by settlement_id desc`, first row); `SDA` 18                       | Inferred |
| SETTLE-R46 | When a claim has no saved settlement the detail action forwards with a null `settlement` attribute, and `detail.jsp` line 24 (`<bean:write name="settlement" .../>`) fails on the missing bean. | `SDA` 18-23; `SettlementDAO` 109 (returns `null`); `detail.jsp` 24                                  | Inferred |
| SETTLE-R47 | `SettlementService.calculateAndSave` is not called by any settlement action and uses different defaults (`settlementId = claimId + 10000`, `calculatedBy = "supervisor"`, `calculatedDate = "2019-03-01"`) from `SettlementSaveAction`. | `SettlementService` 19-31; `SCA` and `SSA` import only `SettlementCalculator` and `SettlementDAO`   | Inferred |
| SETTLE-R48 | Because `calculated_by` is built with `String.valueOf`, a session with no `user` attribute persists the literal string `"null"` rather than failing.        | `SSA` 36-37                                                                                        | Inferred |
| SETTLE-R49 | On `/settlement/calculate`, the policy limit falls back to 10000 when the claim or its policy cannot be found (OQ-03).                                        | `SCA` 25-31                                                                                        | Inferred |
| SETTLE-R50 | The save action does not reuse a previously calculated result; it recomputes the settlement from the request parameters with the same `SettlementCalculator` as calculate. | `SSA` 30-33; `SCA` 34-38                                                                           | Inferred |
| SETTLE-R51 | The save action contains no update to the `CLAIM` row.                                                                                                      | `SSA` 24-45                                                                                        | Inferred |

## 4. Scenario matrix

| Scenario                        | Inputs (covered / deductible / depreciation, claim)   | Limit used | Expected fields                                                                                  | Rules exercised                                   |
|---------------------------------|--------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------|---------------------------------------------------|
| `settlement_calculate`          | 5000.00 / 500.00 / 0.00, claim 119                     | 1000.00    | covered 5000.00, deductible 500.00, depreciation 0.00, capped true, settlement 1000.00           | R01, R04, R07, R14, R21, R22, R24, R34, R35       |
| `settlement_save`               | 5000.00 / 500.00 / 0.00, claim 119                     | 1000.00    | settlement 1000.00, savedBy supervisor; probe `settlement.claim.119.amount = 1000.00`             | R02, R08, R20, R37, R43                            |
| `settlement_blank_deductible`   | 5000.00 / "" / 500.00, claim 120                        | (not capped)| covered 5000.00, deductible 0.00, depreciation 500.00, capped false, settlement 4500.00; probe `claim.120.status = CLOSED` | R16, R21, R26, R44                                 |
| `settlement_half_cent`          | 1.005 / "" / 0.00, claim 120                            | (not capped)| covered 1.01, deductible 0.00, depreciation 0.00, capped false, settlement 1.00                  | R16, R28, R29, R31, R34                            |
| `settlement_policy_cap`         | 20000.00 / 100.00 / 0.00, claim 119                     | 1000.00    | covered 20000.00, deductible 100.00, depreciation 0.00, capped true, settlement 1000.00          | R14, R22, R24                                      |
| `settlement_deductible_floor`   | 1000.00 / 2000.00 / 0.00, claim 120                     | (not capped)| covered 1000.00, deductible 2000.00, depreciation 0.00, capped false, settlement 0.00            | R23, R26, R30, R33                                 |
| `E:` non-numeric deductible (not a transcript) | 1000 / `abc` / 0, claim 120                | n/a        | HTTP 200, forward `/WEB-INF/jsp/error.jsp`, no business fields                                   | R18                                                |
| CHG-001 future state (no transcript yet)       | 1000 / `abc` / 0, claim 120                | n/a        | HTTP 200, forward `/WEB-INF/jsp/settlement/calculate.jsp`, `validation_errors: [settlement.deductible.invalid]`, no settlement computed | R18 v2                                             |

The policy limit of 1000.00 for claim 119 is not stated in any transcript; it
is inferred from the two capped scenarios, both of which settle at exactly
1000.00 from different inputs. The limit for claim 120 is not observable from
the transcripts beyond being at least 4500.00.

## 5. Open questions

Each item needs a business decision before the corresponding rule, whatever
its evidence status, can be adopted as a requirement. Closed items keep their
id and record the decision and the change record that made it.

| ID     | Question                                                                                                                                                                                                                          | Related rules        |
|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| OQ-01  | **Default claim 119.** `SettlementCalculateAction`, `SettlementSaveAction` and `SettlementDetailAction` all fall back to claim 119 when `claimId` is missing or not an integer (`SCA` 23, `SSA` 24, `SDA` 17). Should a missing claim be an error instead, and if a default is kept, is 119 the intended value or a leftover from testing? | R11, R10             |
| OQ-02  | **Default covered amount 5000.** A missing or non-numeric `coveredAmount` silently becomes 5000 (`SCA` 32, `SSA` 27), which on `/settlement/save` writes a real settlement row. Should a missing covered amount be rejected, default to 0, or default to a claim-derived value (for example the reserve)? | R12, R10             |
| OQ-03  | **Default policy limit 10000 when the policy is missing.** On calculate only, a claim that does not exist or has no policy is capped at 10000 (`SCA` 25-31); on save the same condition throws (`SSA` 26, 33). Which behaviour is intended, and should the two actions agree? | R49, R14, R15        |
| OQ-04  | **Non-blank, non-numeric deductible. Closed by CHG-001 (Ben Lau, 2026-09-20).** `deductible` is the only input parsed without a fallback (`Calculator` 28-29); `abc` produces a `NumberFormatException` that the global exception handler turns into HTTP 200 with the generic `error.jsp` (R18, current state). Decision: it is rejected with the field-level validation key `settlement.deductible.invalid`, the calculate screen is redisplayed with HTTP 200, and no settlement is computed (R18 v2); a blank deductible stays 0.00 (R16, R17). The scope questions the decision leaves open are OQ-15. | R18, R18 v2, R16, R17 |
| OQ-05  | **Money rounding method.** The settlement amount is rounded with `Math.round(amount * 100.0) / 100.0` in double arithmetic (`Calculator` 37), which turns 1.005 into 1.00, while the same screen displays the 1.005 input as 1.01 (`settlement_half_cent`). Is the required rule round-half-up on the decimal value, round-half-even, or the current binary behaviour, and should inputs be rounded on entry so display and calculation agree? | R28, R29, R31, R34   |
| OQ-06  | **Cap at exactly the limit.** An amount after deductible exactly equal to the policy limit is reported as not capped (`Calculator` 35). Is `cappedAtLimit` meant to describe "reached the limit" or "exceeded the limit"?               | R24, R25             |
| OQ-07  | **Zero or negative inputs.** No settlement input is checked for sign or magnitude (R19), so a negative covered amount, negative deductible or negative depreciation is calculated as submitted. Should `financialAmount` (`CAS` 328-330) or an equivalent be applied?                          | R19                  |
| OQ-08  | **Save link semantics.** The "Save settlement" link on the calculate screen (`calculate.jsp` 30) is a parameterless GET to `save.do`, so the persisted settlement is built from the fallback values (claim 119, 5000, 0, 0) rather than the figures the operator just reviewed. Should save carry the reviewed figures (form re-post or session state), and should save be POST-only?            | R10, R37, R50        |
| OQ-09  | **Fixed calculated date.** Saved settlements always record `2019-04-01` (`SSA` 38) and `SettlementService` records `2019-03-01`. Should the date come from the clock, from the configured as-of date, or from operator input?                                                                | R41, R47             |
| OQ-10  | **Multiple settlements per claim.** Each save inserts a new row and the detail screen shows only the latest (R38, R45). Is a history of settlements intended, or should a claim have at most one settlement that is replaced on save?                                                       | R38, R45             |
| OQ-11  | **Detail for a claim with no settlement.** The detail screen fails on a missing bean when no settlement exists (R46). What should the operator see?                                                                                | R46, R09             |
| OQ-12  | **Meaning of the `settlement.claim.<id>.amount` probe.** `transcripts/README.md` resolves this probe through the "settlement calculation endpoint", not the detail screen, so `settlement_save`'s `db_state` demonstrates that calculate returns 1000.00 for claim 119 with default inputs rather than that a row was persisted. Should the probe be redirected to `/settlement/detail.do` before R43 is relied on as evidence of persistence? | R43, R11, R12, R14   |
| OQ-13  | **Dead service path.** `SettlementService.calculateAndSave` is unused and disagrees with `SettlementSaveAction` on id allocation, operator and date (R47). Should it be removed or should the action delegate to it?                                                                          | R47                  |
| OQ-14  | **Operator when no session user.** `calculated_by` becomes the literal string `"null"` if the session has no `user` (R48). Given the screens run behind authentication, is this reachable, and if so should it be rejected?                                                                  | R48, R40, R20        |
| OQ-15  | **Scope of the CHG-001 deductible check.** CHG-001 decides `/settlement/calculate.do` only. (a) `/settlement/save.do` recomputes with the same calculator (R02, R50), so `deductible=abc` on save still reaches `error.jsp`; must save reject it too, and how, given it has no form screen to redisplay? (b) Is a whitespace-only deductible "blank" (0.00, R17) or "not a valid number" (R18 v2)? (c) On redisplay no `settlement` attribute exists, so what do the five `f_NAME` spans of R07 show: empty values or the submitted inputs? | R18 v2, R02, R50, R17, R07, R04 |

## 6. Change log

| Version | Date       | Author | Change                                                                                             |
|---------|------------|--------|----------------------------------------------------------------------------------------------------|
| 0.1     | 2026-09-20 | Devin  | Initial draft derived from the six settlement transcripts and the settlement source files listed in section 2. 48 rules (SETTLE-R01 to SETTLE-R48), 14 open questions. No code or transcript changes. |
| 0.2     | 2026-09-20 | Devin (reviewer: Ben Lau) | Reviewer corrections. R18 rewritten from execution evidence: non-numeric deductible returns HTTP 200 with forward `/WEB-INF/jsp/error.jsp` via the Struts global exception handler; new `E:` citation kind (2.3). Status column restricted to evidence only: R14, R29, R44 become `Observed` (transcript-evidenced), R10, R11, R12 become `Inferred` (source-only); pending decisions live solely in section 5 (OQ-01 to OQ-05, OQ-08). Source-only clauses split out of `Observed` rules into R49 (limit fallback, from R14), R50 (save recomputes, from R37) and R51 (save has no claim update, from R44); R40 becomes `Inferred` because the stored column is not probed. R45 no longer cites the dormant `SettlementService`. 51 rules (SETTLE-R01 to SETTLE-R51). |
| 0.3     | 2026-09-20 | Devin (reviewer: Ben Lau; CHG-001 owner: Ben Lau) | Requirement change CHG-001 (`docs/changes/CHG-001-invalid-deductible.md`). SETTLE-R18 kept as the current-state rule; SETTLE-R18 v2 added beneath it as approved future state (`Open`): invalid non-blank deductible redisplays the calculate screen with `settlement.deductible.invalid`, HTTP 200, no settlement computed. Versioned-rule convention added to section 1 and the `Open` legend in 2.4. OQ-04 closed with that decision; OQ-15 opened for the scope questions it leaves (save route, whitespace boundary, screen content on redisplay). Section 4 gains the CHG-001 future-state row. Review fix: R10 now cites R16 (absent deductible) instead of R17 (whitespace). No `src/` or `transcripts/` change. |
