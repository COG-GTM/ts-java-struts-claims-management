# SPEC-SETTLE-001: Settlement module behavioural specification

Version: 0.1
Status: draft
Scope: the `/settlement` screens of the NorthStar claims application (calculate, save, detail).

## 1. Purpose and method

This document records how the settlement module behaves today. It is derived only from:

- six captured transcripts: `transcripts/settlement_calculate.json`, `settlement_save.json`,
  `settlement_blank_deductible.json`, `settlement_half_cent.json`, `settlement_policy_cap.json`,
  `settlement_deductible_floor.json`;
- the source of `SettlementCalculateAction`, `SettlementSaveAction`, `SettlementDetailAction`,
  `SettlementForm`, `ClaimsActionSupport`, `SettlementCalculator`, `SettlementService`,
  `SettlementDAO`, `PolicyDAO`;
- the three `/settlement` action mappings in `src/main/webapp/WEB-INF/struts-config.xml`;
- the JSPs under `src/main/webapp/WEB-INF/jsp/settlement`.

No behaviour has been inferred from product intent, documentation or tickets. Nothing under `src`
or `transcripts` was modified to produce this specification.

Every rule carries a status:

| Status | Meaning |
| --- | --- |
| Observed | Demonstrated by at least one transcript. |
| Inferred | Read from code only; no transcript exercises it. |
| Open | Behaviour exists but the correct behaviour is a business decision (see section 8). |

Citations are `file:line` against the commit this document was written on, or the transcript
scenario name.

## 2. Vocabulary

- **covered amount**: the submitted loss amount before any reduction (`coveredAmount`).
- **depreciation**: an amount subtracted from the covered amount (`depreciation`).
- **deductible**: the policyholder retention subtracted after depreciation (`deductible`, echoed as
  `deductibleApplied`).
- **policy limit**: `POLICY.policy_limit` of the policy attached to the claim.
- **settlement amount**: the payable result after depreciation, deductible, floor, cap and rounding.
- **capped at limit**: flag stating the policy limit, not the arithmetic, determined the amount.

## 3. Routing and screens

| ID | Rule | Evidence | Status |
| --- | --- | --- | --- |
| SETTLE-R01 | `POST /claims/settlement/calculate.do` is handled by `SettlementCalculateAction` and forwards to `/WEB-INF/jsp/settlement/calculate.jsp` with HTTP 200. | scenarios `settlement_calculate`, `settlement_policy_cap`, `settlement_blank_deductible`, `settlement_half_cent`, `settlement_deductible_floor`; `struts-config.xml:205-213` | Observed |
| SETTLE-R02 | `POST /claims/settlement/save.do` is handled by `SettlementSaveAction` and forwards to `/WEB-INF/jsp/settlement/save.jsp` with HTTP 200. | scenario `settlement_save`; `struts-config.xml:214-222` | Observed |
| SETTLE-R03 | `/claims/settlement/detail.do` is handled by `SettlementDetailAction` and forwards to `/WEB-INF/jsp/settlement/detail.jsp`. | `struts-config.xml:356-363`; `SettlementDetailAction.java:19-23` | Inferred |
| SETTLE-R04 | None of the three mappings runs Struts validation (`validate="false"`), so no settlement request produces validation errors. | `struts-config.xml:210,219,359`; `validation_errors: []` in all six scenarios | Observed |
| SETTLE-R05 | The `settlementForm` bean is declared on the calculate and save mappings but is not read by the actions, which take every input from `request.getParameter`. | `struts-config.xml:208,217`; `SettlementForm.java:11-75`; `SettlementCalculateAction.java:23-34`; `SettlementSaveAction.java:24-29` | Inferred |
| SETTLE-R06 | The detail mapping declares no form bean and the action reads no input other than a `claimId` request parameter, which the container supplies from the query string or a form body under any HTTP method. | `struts-config.xml:356-363`; `SettlementDetailAction.java:17-22` | Inferred |

## 4. Calculation

All arithmetic is performed by `SettlementCalculator.calculate` on primitive `double` values.

| ID | Rule | Evidence | Status |
| --- | --- | --- | --- |
| SETTLE-R07 | The payable amount before limit and rounding is `coveredAmount - depreciation - deductible`. | `SettlementCalculator.java:30-31`; scenario `settlement_blank_deductible` (5000.00 − 500.00 − 0 = 4500.00) | Observed |
| SETTLE-R08 | A negative result after depreciation and deductible is floored to `0.00` rather than reported as a negative settlement. | `SettlementCalculator.java:32-34`; scenario `settlement_deductible_floor` (1000.00 − 2000.00 yields 0.00) | Observed |
| SETTLE-R09 | When the floored amount exceeds the policy limit, the settlement amount becomes the policy limit and `cappedAtLimit` is `true`. | `SettlementCalculator.java:35-36`; scenarios `settlement_policy_cap` and `settlement_calculate` (both return 1000.00 with `cappedAtLimit=true`) | Observed |
| SETTLE-R10 | The cap comparison is strictly greater than, so an amount exactly equal to the policy limit is paid in full with `cappedAtLimit=false`. | `SettlementCalculator.java:35` | Inferred |
| SETTLE-R11 | `cappedAtLimit` is `false` whenever the limit did not bind, including when the amount was floored to zero. | `SettlementCalculator.java:35-36`; scenarios `settlement_deductible_floor`, `settlement_blank_deductible`, `settlement_half_cent` | Observed |
| SETTLE-R12 | A missing or empty `deductible` parameter is treated as zero and reported as `deductibleApplied = 0.00`. | `SettlementCalculateAction.java:34-37`; `SettlementCalculator.java:26-29`; scenarios `settlement_blank_deductible`, `settlement_half_cent` | Observed |
| SETTLE-R13 | `deductibleApplied` echoes the full submitted deductible even when the floor means only part of it was absorbed. | `SettlementCalculator.java:40`; scenario `settlement_deductible_floor` (`deductibleApplied=2000.00` against a 1000.00 loss) | Observed |
| SETTLE-R14 | `coveredAmount`, `deductibleApplied` and `depreciation` are carried onto the settlement exactly as submitted, with no cap, floor or rounding applied by the calculator; the view still reformats them for display (SETTLE-R35). | `SettlementCalculator.java:39-41`; scenario `settlement_policy_cap` (`coveredAmount=20000.00` alongside a 1000.00 settlement) | Observed |
| SETTLE-R15 | The settlement amount is rounded to cents as `Math.round(amount * 100.0) / 100.0`, which rounds the binary `double`, so the decimal half-cent 1.005 settles at 1.00. | `SettlementCalculator.java:37`; scenario `settlement_half_cent` (`settlementAmount=1.00` from a 1.005 loss) | Observed |
| SETTLE-R16 | The policy limit used by calculate is the `policy_limit` of the policy referenced by the claim. | `SettlementCalculateAction.java:24-31`; `PolicyDAO.java:26-44`; scenario `settlement_policy_cap` (claim 119 caps at 1000.00) | Observed |
| SETTLE-R17 | When the claim cannot be loaded, or the claim has no policy row, calculate falls back to a policy limit of 10000. | `SettlementCalculateAction.java:25-31`; `ClaimsActionSupport.java:93-100` | Inferred |
| SETTLE-R18 | `SettlementCalculateAction` also publishes the effective policy limit to the view as `policyLimit`, although no settlement JSP renders it. | `SettlementCalculateAction.java:40`; `calculate.jsp:8-44` | Inferred |

## 5. Input handling

| ID | Rule | Evidence | Status |
| --- | --- | --- | --- |
| SETTLE-R19 | A missing or non-numeric `claimId` falls back to claim 119 on all three screens. | `ClaimsActionSupport.java:31-37`; `SettlementCalculateAction.java:23`; `SettlementSaveAction.java:24`; `SettlementDetailAction.java:17` | Inferred |
| SETTLE-R20 | A missing or non-numeric `coveredAmount` falls back to 5000 and a missing or non-numeric `depreciation` falls back to 0, silently and without any error to the operator. | `ClaimsActionSupport.java:39-45`; `SettlementCalculateAction.java:32-33`; `SettlementSaveAction.java:27-28` | Inferred |
| SETTLE-R21 | A non-blank, non-numeric `deductible` is not defaulted; it reaches `Double.parseDouble` unguarded and the request fails with an unhandled `NumberFormatException` instead of a validation message. | `SettlementCalculateAction.java:34-37`; `SettlementCalculator.java:27-28` | Open |
| SETTLE-R22 | Inputs are accepted regardless of sign, so a negative covered amount, depreciation or deductible is arithmetic input rather than an error. | `SettlementCalculator.java:24-37`; `ClaimsActionSupport.java:39-45` | Inferred |
| SETTLE-R23 | The claim's status does not gate settlement: a CLOSED claim is calculated normally. | scenario `settlement_blank_deductible` (probe `claim.120.status = CLOSED`, request still returns a settlement) | Observed |

## 6. Persistence

| ID | Rule | Evidence | Status |
| --- | --- | --- | --- |
| SETTLE-R24 | Calculate writes nothing: no settlement, claim or policy row changes as a result of `/settlement/calculate.do`. | `SettlementCalculateAction.java:20-43`; `db_state: {}` in `settlement_calculate`, `settlement_half_cent`, `settlement_policy_cap`, `settlement_deductible_floor` | Observed |
| SETTLE-R25 | Save recomputes the settlement from the submitted parameters rather than reusing anything produced by the calculate screen; the two screens share no server-side state. | `SettlementSaveAction.java:27-33` | Inferred |
| SETTLE-R26 | Save persists one `SETTLEMENT` row with the recomputed amount, and the stored amount matches the amount the calculate screen showed for the same inputs. | `SettlementSaveAction.java:34-39`; `SettlementDAO.java:118-142`; scenario `settlement_save` (`settlement.claim.119.amount = 1000.00`, same as `settlement_calculate`) | Observed |
| SETTLE-R27 | The settlement identifier is allocated as `max(settlement_id) + 1` read outside any transaction covering the insert. | `SettlementSaveAction.java:34`; `ClaimsActionSupport.java:61-75` | Inferred |
| SETTLE-R28 | Save always inserts; it never updates an existing settlement, so a claim accumulates one row per save. | `SettlementDAO.java:118-142` | Inferred |
| SETTLE-R29 | `calculatedBy` is the session `user` attribute, and the save screen shows it back as `savedBy`. | `SettlementSaveAction.java:36-37`; `save.jsp:21-22`; scenario `settlement_save` (`savedBy = supervisor`) | Observed |
| SETTLE-R30 | `calculatedDate` is stored as the constant `2019-04-01` and not as the date of the save. | `SettlementSaveAction.java:38` | Inferred |
| SETTLE-R31 | Save has no policy fallback: it dereferences the claim and the policy directly, so a missing claim or policy fails the request with a `NullPointerException` rather than the 10000 default used by calculate. | `SettlementSaveAction.java:25-26,33`; contrast `SettlementCalculateAction.java:25-31` | Inferred |
| SETTLE-R32 | The detail screen shows the settlement with the highest `settlement_id` for the claim; no timestamp is stored, so insertion order is only implied by the `max + 1` allocation in SETTLE-R27. | `SettlementDetailAction.java:18`; `SettlementDAO.java:100-115` | Inferred |
| SETTLE-R33 | `SettlementService` is not used by any of the three actions; its own conventions (`settlementId = claimId + 10000`, `calculatedBy = "supervisor"`, `calculatedDate = "2019-03-01"`) therefore describe no live behaviour. | `SettlementService.java:19-31`; `SettlementCalculateAction.java:35-39`; `SettlementSaveAction.java:30-39` | Inferred |

## 7. Presentation

| ID | Rule | Evidence | Status |
| --- | --- | --- | --- |
| SETTLE-R34 | The calculate screen shows covered amount, deductible applied, depreciation, capped flag and settlement amount, and nothing else about the claim. | `calculate.jsp:18-29`; business fields of the five calculate scenarios | Observed |
| SETTLE-R35 | Money fields are rendered with `String.format("%.2f", ...)`, which rounds the shortest decimal representation half-up, so a 1.005 covered amount displays as `1.01` while the settlement computed from it displays as `1.00`. | `FieldTag.java:52-58`; `SettlementCalculator.java:37`; scenario `settlement_half_cent` (`coveredAmount=1.01`, `settlementAmount=1.00`) | Observed |
| SETTLE-R36 | The capped flag is declared as a money field but is not numeric, so the formatter falls back to the raw text and the screen shows `true` or `false`. | `calculate.jsp:26`; `FieldTag.java:52-58`; `cappedAtLimit` values in all five calculate scenarios | Observed |
| SETTLE-R37 | The save screen shows only the settlement amount and the saving operator; it repeats neither the inputs nor the capped flag. | `save.jsp:19-22`; business fields of `settlement_save` | Observed |
| SETTLE-R38 | The detail screen additionally shows the settlement id, claim id, `calculatedBy` and `calculatedDate`. | `detail.jsp:10-26` | Inferred |
| SETTLE-R39 | The detail link on the calculate screen is built from a `claimId` request attribute that the calculate action never sets, so the link carries an empty `claimId` and detail then falls back to claim 119. | `calculate.jsp:17`; `SettlementCalculateAction.java:38-42`; `SettlementDetailAction.java:17` | Inferred |
| SETTLE-R40 | The save link on the calculate screen carries no parameters, so following it submits an empty form and save recomputes from its own defaults rather than the displayed figures. | `calculate.jsp:30`; `SettlementSaveAction.java:24-33` | Inferred |
| SETTLE-R41 | When a claim has no settlement row, the detail action forwards with a null `settlement`, and `bean:write` for `calculatedBy` has no bean to read. | `SettlementDetailAction.java:18-23`; `SettlementDAO.java:100-115`; `detail.jsp:24` | Inferred |

## 8. Open questions

1. **Hardcoded claim default (`claimId = 119`).** All three actions silently settle claim 119 when
   `claimId` is absent or unparseable (`SettlementCalculateAction.java:23`,
   `SettlementSaveAction.java:24`, `SettlementDetailAction.java:17`). Is this a fixture left in
   place, or intended behaviour? A save against the wrong claim writes a real row, so the
   replacement should probably be a rejected request. Decision needed on the error surface.
2. **Hardcoded covered amount default (`coveredAmount = 5000`).** A missing or malformed covered
   amount produces a 5000.00 loss instead of an error (`SettlementCalculateAction.java:32`,
   `SettlementSaveAction.java:27`). Confirm whether covered amount is mandatory.
3. **Hardcoded policy limit default (`10000` when the policy is missing).** Calculate falls back to
   a 10000 limit when the claim or its policy cannot be loaded
   (`SettlementCalculateAction.java:25-31`), while save has no fallback at all and fails
   (`SettlementSaveAction.java:26,33`). Two questions: should an unknown policy be settleable at
   all, and should calculate and save agree?
4. **Non-blank, non-numeric deductible.** Blank is 0.00 (SETTLE-R12) but `abc` reaches
   `Double.parseDouble` and throws (`SettlementCalculator.java:27-28`). Required behaviour: a field
   validation message, a 0.00 default like the blank case, or the current failure?
5. **Money rounding method.** The calculator rounds with `Math.round(amount * 100.0) / 100.0` on a
   binary `double` (`SettlementCalculator.java:37`) while the view formats with `%.2f`
   (`FieldTag.java:52-58`). The two disagree at a half cent: 1.005 settles at 1.00 but displays
   elsewhere as 1.01 (`settlement_half_cent`). Which rounding is contractual (half-up on the
   decimal value, half-even, or truncate), and at which point is it applied?
6. **Cap boundary.** The cap triggers on strictly greater than the limit (SETTLE-R10), so an amount
   exactly at the limit reports `cappedAtLimit=false`. Confirm whether "at the limit" should read as
   capped.
7. **Negative and zero inputs.** Negative covered amounts, depreciation or deductibles are accepted
   (SETTLE-R22). Confirm the permitted ranges.
8. **Repeated saves.** Save always inserts (SETTLE-R28), so settling twice leaves two rows and only
   the latest is visible on detail (SETTLE-R32). Confirm whether re-settlement should replace,
   version or be blocked.
9. **`calculatedDate` constant.** Saves record `2019-04-01` (SETTLE-R30). Confirm this should be the
   actual save timestamp.
10. **Status gating.** A CLOSED claim can be settled (SETTLE-R23). Confirm which claim statuses
    permit settlement.
11. **Deductible echo when floored.** `deductibleApplied` reports the submitted deductible rather
    than the portion actually absorbed (SETTLE-R13). Confirm which figure belongs on the screen and
    in the stored row.

## 9. Change log

| Version | Date | Author | Change |
| --- | --- | --- | --- |
| 0.1 | 2026-09-20 | Devin (for Ben Lau) | First draft. Rules SETTLE-R01 to SETTLE-R41 derived from the six settlement transcripts and the settlement source, mappings and JSPs. |
