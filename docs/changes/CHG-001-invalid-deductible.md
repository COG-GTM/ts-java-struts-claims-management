# CHG-001: Invalid deductible must redisplay the calculate screen

| Field       | Value                                                                     |
|-------------|---------------------------------------------------------------------------|
| Identifier  | CHG-001                                                                   |
| Owner       | Ben Lau                                                                   |
| Date        | 2026-09-20                                                                |
| Status      | Approved (future state); not yet implemented in `src/`                    |
| Spec        | `docs/specs/SPEC-SETTLE-001.md` rule SETTLE-R18 (current state) and SETTLE-R18 v2 (future state, added by this change) |
| Baseline    | `docs/TRACEABILITY.md` as generated at spec version 0.2                   |

## 1. Requirement

A non-blank `deductible` that is not a valid number must no longer reach the
generic error page. `/settlement/calculate.do` must redisplay the calculate
screen with the validation key `settlement.deductible.invalid` and HTTP 200,
and no settlement is computed.

## 2. Rule covered

SETTLE-R18 is the rule that covers the invalid deductible. Its current-state
text (spec 0.2) says the request returns HTTP 200 with the forward
`/WEB-INF/jsp/error.jsp`, reproduced by execution evidence
(`E:` as `supervisor`, POST `/claims/settlement/calculate.do`
`claimId=120&coveredAmount=1000&deductible=abc&depreciation=0`). The
`docs/TRACEABILITY.md` row for SETTLE-R18 at spec 0.2 reads:

| rule       | spec version | status   | transcript | decision record                  | commit    | test | parity result | reviewer  |
|------------|--------------|----------|------------|----------------------------------|-----------|------|---------------|-----------|
| SETTLE-R18 | 0.2          | Observed | none       | `ADR-001-settlement-boundary.md` | `02da3b5` | none | none          | `Ben Lau` |

## 3. Impact list

Everything below either references SETTLE-R18 (by id, by the open question
OQ-04 it points to, or by the `deductible=abc` request) or depends on the
deductible parsing that SETTLE-R18 describes. Sources: the SETTLE-R18 row and
the `decision record`/`commit` columns of `docs/TRACEABILITY.md`; the
`Related rules` column of section 5 and the rule cross-references of
`docs/specs/SPEC-SETTLE-001.md`; a text search of `docs/decisions/` and the
branch history for `R18` and `OQ-04`.

### 3.1 Rules

| Rule       | Relationship                                                                                                                                                                                                      | Effect of CHG-001                                                                                                                                                                                           |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SETTLE-R18 | The rule itself.                                                                                                                                                                                                  | Kept as the current-state rule; SETTLE-R18 v2 added beneath it as approved future state.                                                                                                                   |
| SETTLE-R16 | Sibling rule in OQ-04: absent or empty `deductible` is 0.00.                                                                                                                                                       | Unchanged. CHG-001 applies only to non-blank values; blank still means 0.00.                                                                                                                                |
| SETTLE-R17 | Sibling rule in OQ-04: whitespace-only `deductible` passes the action's empty check and is trimmed to 0.00 by the calculator.                                                                                      | Boundary to confirm at implementation: whitespace-only is "blank" (0.00, R17 unchanged) rather than "not a valid number" (R18 v2). The spec keeps R17 as written.                                          |
| SETTLE-R04 | States that the settlement actions never produce Struts validation errors (all six transcripts have `validation_errors: []`; mappings declare `validate="false"`).                                                | Contradicted for the invalid-deductible case once CHG-001 is implemented. R04 stays true for the six transcripts and for `save`/`detail`; it needs its own future-state revision when the implementation path (action-level `nsValidationErrors` as in `IntakeSubmitAction` 36-38, or Struts validation) is chosen. |
| SETTLE-R01 | Calculate "computes a settlement from the request parameters and forwards to `calculate.jsp` with HTTP 200".                                                                                                       | Forward and status unchanged; "computes a settlement" no longer holds for the invalid case (R18 v2: no settlement is computed).                                                                              |
| SETTLE-R07 | The calculate screen renders the five `f_NAME` business fields from the `settlement` request attribute.                                                                                                             | On redisplay there is no `settlement` attribute, so the screen's field content for that response is undefined by the requirement (empty spans versus echoed inputs). Decision recorded as OQ-15.            |
| SETTLE-R02, SETTLE-R50 | Save recomputes with the same `SettlementCalculator` from the request parameters, so `save.do` with `deductible=abc` currently also reaches `error.jsp`.                                              | CHG-001 as worded covers the calculate screen only. Whether `save.do` must reject the same input (and how, since it has no screen to redisplay) is recorded as OQ-15.                                       |
| SETTLE-R05 | The `settlementForm` bean is bound to the mappings but never read by the actions.                                                                                                                                  | Implementation constraint: a Struts-validator route would start reading the form; the existing action-level convention does not.                                                                            |
| SETTLE-R19 | No range or sign checks on any settlement input.                                                                                                                                                                  | Adjacent, not changed: CHG-001 adds a format check for one field, not range or sign checks (OQ-07 stays open).                                                                                              |
| SETTLE-R22, SETTLE-R23, SETTLE-R30, SETTLE-R33 | Consume the resolved deductible after section 3.2.                                                                                                                                             | Unchanged for numeric input; they are simply never reached for an invalid deductible.                                                                                                                       |

### 3.2 Transcripts

| Transcript                     | Relationship                                                                                     | Effect of CHG-001                                                                                                          |
|--------------------------------|--------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| (none)                         | `docs/TRACEABILITY.md` lists no transcript for SETTLE-R18; the evidence is the `E:` request only. | A new scenario (for example `settlement_invalid_deductible`: POST `/claims/settlement/calculate.do`, `claimId=120&coveredAmount=1000&deductible=abc&depreciation=0`, expected `forward:/WEB-INF/jsp/settlement/calculate.jsp`, `status: 200`, `validation_errors: ["settlement.deductible.invalid"]`) is needed once the code changes; it is added through `tools/capture/capture.py` and `make capture`, not by hand. Not done here. |
| `settlement_blank_deductible`  | Evidence for SETTLE-R16 (blank deductible is 0.00).                                              | Must keep passing: blank stays 0.00.                                                                                       |
| `settlement_half_cent`         | Evidence for SETTLE-R16 (no deductible sent).                                                    | Must keep passing.                                                                                                         |
| `settlement_calculate`, `settlement_policy_cap`, `settlement_deductible_floor` | Numeric deductibles (500.00, 100.00, 2000.00).                                | Must keep passing; they define what "a valid number" already accepts.                                                      |
| All six settlement transcripts | `validation_errors: []` (SETTLE-R04).                                                            | Unchanged; none of them sends an invalid deductible.                                                                       |

### 3.3 Open questions

| Question | Relationship                                                            | Effect of CHG-001                                                                                                         |
|----------|-------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| OQ-04    | The question SETTLE-R18 points to: what to do with a non-numeric deductible. | Closed by CHG-001: field-level validation error `settlement.deductible.invalid`, calculate screen redisplayed, HTTP 200, no settlement computed. |
| OQ-07    | Zero or negative inputs (SETTLE-R19).                                    | Stays open; CHG-001 does not decide range or sign rules.                                                                 |
| OQ-15    | New: scope of the check (`save.do`, whitespace boundary, screen content on redisplay). | Opened by this change so that the implementation does not decide it silently.                                            |

### 3.4 Decision records

| Record                                        | Location                                   | Relationship                                                                                                           | Effect of CHG-001                                                                                                                        |
|-----------------------------------------------|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `docs/decisions/ADR-001-settlement-boundary.md` | Context (settlement-alone boundary list)   | Names SETTLE-R18 among the accidental behaviours the extraction must reproduce.                                         | The service must implement R18 v2 instead once CHG-001 is scheduled before extraction, or R18 first and R18 v2 afterwards; see 3.7.     |
|                                               | Decision (extraction contract)             | "the HTTP 200 error page for a non-numeric deductible (SETTLE-R18)" is part of the contract.                            | Contract line changes to the R18 v2 behaviour when CHG-001 is implemented.                                                               |
|                                               | Decision (acceptance gate)                 | Lists the "SETTLE-R18 error page" supplementary check.                                                                  | Check must be re-targeted to R18 v2.                                                                                                     |
|                                               | Consequence 3                              | "Accidental behaviour is preserved on purpose ... (SETTLE-R18; OQ-04)"; says the replay oracle stays valid only while such decisions are not implemented. | CHG-001 is the first such decision to be implemented; the sequencing rule in Consequence 3 applies (3.7).                         |
|                                               | Verification, `r18-error` check            | Expects HTTP 200 and forward `/WEB-INF/jsp/error.jsp` for the `deductible=abc` request.                                 | Expectation becomes forward `/WEB-INF/jsp/settlement/calculate.jsp`, `validation_errors: ["settlement.deductible.invalid"]`, no business fields computed. |
|                                               | Migration plan step 5                      | "the HTTP 200 error page of SETTLE-R18" is a route requirement.                                                         | Becomes the R18 v2 redisplay.                                                                                                            |

### 3.5 Tests

| Test                                                      | Relationship                                                                 | Effect of CHG-001                                                                    |
|-----------------------------------------------------------|------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| (none)                                                    | `docs/TRACEABILITY.md` lists no test for SETTLE-R18; `services/` does not exist. | Nothing to change today.                                                             |
| Planned: rule-named unit test for SETTLE-R18 in `services/settlement-service` (ADR-001 Verification) | Not yet written.                                              | Must be written against R18 v2 (or both versions, if the service ships before CHG-001 is applied to the legacy application). |
| Planned: `r18-error` replay check (ADR-001 Verification)  | Not yet written.                                                             | As in 3.4.                                                                           |

### 3.6 Commits on this branch

| Commit    | Relationship                                                                                                  |
|-----------|---------------------------------------------------------------------------------------------------------------|
| `02da3b5` | SPEC-SETTLE-001 v0.2: R18 rewritten from execution evidence (the only commit naming R18 in `docs/TRACEABILITY.md`). |

### 3.7 Code touchpoints (for information only; `src/` is not modified by this change)

| Location                                                        | Today                                                                                          | Needed for R18 v2                                                                                   |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `SettlementCalculateAction` 34-37                               | Passes a non-blank `deductible` straight to the calculator.                                    | Validate before calculating; on failure set `nsValidationErrors` and forward to the calculate JSP.  |
| `SettlementCalculator` 28-29                                    | `Double.parseDouble` with no fallback throws `NumberFormatException`.                          | Never reached with an invalid value.                                                                |
| `struts-config.xml` 59-60                                       | `global-exceptions` maps `java.lang.Exception` to `/WEB-INF/jsp/error.jsp`.                    | No longer the path for this case.                                                                   |
| `calculate.jsp`                                                 | Has no `<!-- ns:error ... -->` block (compare `intake/new.jsp` 20-26).                        | Must emit the `ns:error` marker so the harness records `validation_errors`.                         |
| `ApplicationResources.properties`                               | Has `settlement.deductible` (the label) but no `settlement.deductible.invalid`.                | Key must be added.                                                                                  |
| `tools/capture/capture.py` `SCENARIOS`                          | No invalid-deductible scenario.                                                                | New scenario as in 3.2, then `make capture`.                                                        |

Sequencing: ADR-001 Consequence 3 says the replay oracle is valid only while
the recorded accidental behaviours are unchanged. CHG-001 must therefore be
applied to the legacy application and captured into a transcript before the
settlement service is accepted (so the service is built against R18 v2 and
the new transcript), or applied to both sides after acceptance in one change.
Applying it to only one side between those points breaks replay by design.

## 4. Not changed by this record

No file under `src/` or `transcripts/` is modified. The spec change is
recorded in `docs/specs/SPEC-SETTLE-001.md` 0.3 (SETTLE-R18 v2, OQ-04 closed,
OQ-15 opened) and the matrix is regenerated by `tools/traceability.py`.
