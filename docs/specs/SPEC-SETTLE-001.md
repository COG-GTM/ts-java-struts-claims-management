# SPEC-SETTLE-001: settlement calculation

Version: 0.5
Status: draft, reviewed by the engineer
Source of truth for behaviour: `transcripts/settlement_*.json`, captured with
`make capture` against the Struts application at commit `225c8d3`.
Code cited: `src/main/java/com/northstar/claims/web/SettlementCalculateAction.java`,
`SettlementSaveAction.java`, `SettlementDetailAction.java`,
`src/main/java/com/northstar/claims/service/SettlementCalculator.java`,
`src/main/java/com/northstar/claims/web/tag/FieldTag.java`.

One rule per identifier. Identifiers are stable: a rule that changes gets a new
version suffix (`v2`) and the old text stays in the history table at the end.
"Observed" means a transcript shows it. "Read" means only the code shows it and
no transcript exercises it.

## Inputs

### SETTLE-R01 Request parameters
The screen accepts `claimId`, `coveredAmount`, `deductible` and `depreciation`
as request parameters on `POST /settlement/calculate.do`. The `settlementForm`
bean is bound by Struts but the action reads the raw parameters, so no Struts
validation runs. Observed: every settlement transcript.

### SETTLE-R02 Claim identifier fallback
A missing or non-numeric `claimId` is treated as `119`. Read:
`SettlementCalculateAction`, `integer(request.getParameter("claimId"), 119)`.

### SETTLE-R03 Amount fallbacks
Superseded by SETTLE-R03 v2 (version 0.3); text kept for the history.
A missing or non-numeric `coveredAmount` is treated as `5000`; a missing or
non-numeric `depreciation` is treated as `0`. Read: `decimal(...)` calls in
`SettlementCalculateAction`.

### SETTLE-R03 v2 Amount fallbacks
A missing or non-numeric `coveredAmount` is treated as `5000`; a missing or
non-numeric `depreciation` is treated as `0`. "Numeric" means accepted by
`Double.parseDouble`: `1d`, `0x1.0p0`, `Infinity`, `NaN` and values with
surrounding whitespace are numeric, `1,000` is not, and there is no length
limit (QUIRK-08, OQ-05). Not a behaviour change: v1 left "numeric"
undefined and the service had read it as `BigDecimal`. Read:
`ClaimsActionSupport.decimal` (`Double.parseDouble(value)` with the fallback
on any exception), `decimal(...)` calls in `SettlementCalculateAction`.

### SETTLE-R04 Blank deductible
A blank or missing `deductible` is treated as `0.00` and shown as
`deductibleApplied 0.00`. Observed: `settlement_blank_deductible`,
`settlement_half_cent`.

### SETTLE-R05 Policy limit
Superseded by SETTLE-R05 v2 (version 0.4); text kept for the history.
The policy limit is the `policy_limit` of the policy attached to the claim.
If the claim does not exist, or its policy does not exist, the limit is
`10000` and the calculation still runs. Observed: `settlement_calculate`
(claim 119, policy 9001, limit 1000). Read: `SettlementCalculateAction`,
`double limit = 10000;` before the lookups; confirmed by a manual request with
`claimId=9999`, which returned `settlementAmount 90.00` for covered 100 and
deductible 10. Open question: OQ-01.

### SETTLE-R05 v2 Policy limit
As SETTLE-R05, and the claim lookup itself may fail: `ClaimsActionSupport.findClaim`
catches every exception from `ClaimDAO.findById`, logs a warning and returns
`null`, so a failed claim lookup behaves like a missing claim and calculate
still answers with the `10000` limit. The policy lookup
(`new PolicyDAO().findById(claim.getPolicyId())`) is outside that catch, so a
failed policy lookup is a system error (SETTLE-R06 screen). Save uses the same
`findClaim` but dereferences the result without a null check (SETTLE-R12), so
there a failed lookup is a system error. Observed: `settlement_calculate`
(claim 119, policy 9001, limit 1000). Read: `ClaimsActionSupport.findClaim`,
`SettlementCalculateAction` lines 24 to 29; the lookup-failure path has no
transcript because the capture harness cannot make the `CLAIM` query fail.
Open question: OQ-01.

### SETTLE-R06 Non-numeric deductible
Superseded by SETTLE-R06 v2 (version 0.5); text kept for the history.
A `deductible` that is not a number ends in the system error screen
(`errors.system`, HTTP 200, no business fields). Observed:
`settlement_bad_deductible`.

### SETTLE-R06 v2 Non-numeric deductible and the error screen
A `deductible` that is not a number throws `NumberFormatException` inside
`SettlementCalculator.calculate` (`Double.parseDouble(deductible)`), and any
unhandled exception answers the same way: screen `error`, HTTP 200, no
business fields and an empty validation error list. `errors.system` is static
text on `error.jsp`, not an `ns:error` marker, so it is not a validation
error key. Kept as legacy behaviour (QUIRK-05). Observed:
`settlement_bad_deductible` (`result forward:/WEB-INF/jsp/error.jsp`,
`status 200`, `validation_errors []`). Read: `struts-config.xml`
`<global-exceptions>` (`java.lang.Exception` to `error.jsp`), `error.jsp`.

## Calculation

### SETTLE-R07 Net amount and floor
`net = coveredAmount - depreciation - deductible`. If `net` is below zero it
becomes `0.00`. Observed: `settlement_deductible_floor` (1000 - 0 - 2000 gives
`0.00`), `settlement_blank_deductible` (5000 - 500 - 0 gives `4500.00`).

### SETTLE-R08 Policy cap
If `net` exceeds the policy limit, the settlement amount is the limit and
`cappedAtLimit` is `true`; otherwise `cappedAtLimit` is `false`. Observed:
`settlement_calculate` (4500 capped to `1000.00`), `settlement_policy_cap`
(19900 capped to `1000.00`), `settlement_half_cent` (`false`).

### SETTLE-R09 Rounding
Superseded by SETTLE-R09 v2 (version 0.3); text kept for the history.
The settlement amount is rounded to the nearest cent. Observed:
`settlement_half_cent` (input `1.005` gives `1.00`).

### SETTLE-R09 v2 Rounding
The arithmetic of SETTLE-R07 and SETTLE-R08 runs on `double` and the
settlement amount is `Math.round(amount * 100.0) / 100.0`, which is not
half-up rounding of the decimal value: `1.005` gives `1.00` (QUIRK-01,
QUIRK-08). Not a behaviour change: v1 said "nearest cent", which the
observed `1.00` already contradicted for a decimal reading. Observed:
`settlement_half_cent`. Read: `SettlementCalculator.calculate`, the
`double rounded = Math.round(amount * 100.0) / 100.0;` statement.

### SETTLE-R10 Display format
Superseded by SETTLE-R10 v2 (version 0.5); text kept for the history.
Money fields (`coveredAmount`, `deductibleApplied`, `depreciation`,
`settlementAmount`) are shown with exactly two decimals. `cappedAtLimit` is
shown as `true` or `false`. Observed: every settlement transcript with a
result screen.

### SETTLE-R10 v2 Display format
Money fields (`coveredAmount`, `deductibleApplied`, `depreciation`,
`settlementAmount`) are shown as `String.format("%.2f", double)`, which is
a second, separate rounding of the decimal representation, half up: for input
`1.005` the screen shows `coveredAmount 1.01` next to `settlementAmount 1.00`
(SETTLE-R09 v2). Both values are kept as legacy behaviour (QUIRK-01,
QUIRK-02). `cappedAtLimit` is shown as `true` or `false`. Observed: every
settlement transcript with a result screen; `settlement_half_cent` for the
`1.01` / `1.00` pair. Read: `FieldTag.formatValue`, the
`String.format("%.2f", ...)` branch for `type="money"`.

## Persistence

### SETTLE-R11 Calculate does not write
`POST /settlement/calculate.do` inserts nothing. Observed: `db_state` is
empty for every calculate transcript; Read: no DAO write in
`SettlementCalculateAction`.

### SETTLE-R12 Save
`POST /settlement/save.do` recalculates with the same rules, inserts one
`SETTLEMENT` row with `settlement_id = max(settlement_id) + 1`,
`calculated_by` set to the logged-in user and `calculated_date` fixed at
`2019-04-01`, and shows `settlementAmount` and the user. Observed:
`settlement_save` (`settlementAmount 1000.00`, `savedBy supervisor`). Read:
`SettlementSaveAction`, `SettlementDAO.save`.

### SETTLE-R13 Detail
`GET /settlement/detail.do?claimId=N` shows the `SETTLEMENT` row with the
highest `settlement_id` for that claim. Read: `SettlementDAO.findByClaim`
(`order by settlement_id desc`).

## Rule history

| Rule | Version | Change |
| --- | --- | --- |
| all | 0.1 | first draft from transcripts and code |
| SETTLE-R05 | 0.2 | engineer added the `10000` fallback the draft missed; raised OQ-01 |
| SETTLE-R03 | 0.3 | superseded by SETTLE-R03 v2 |
| SETTLE-R03 v2 | 0.3 | pull request review: "numeric" means accepted by `Double.parseDouble` (`1d`, `0x1.0p0`, `Infinity`, `NaN`, surrounding whitespace, no length limit); recorded as QUIRK-08, raised OQ-05 |
| SETTLE-R09 | 0.3 | superseded by SETTLE-R09 v2 |
| SETTLE-R05 | 0.4 | superseded by SETTLE-R05 v2 |
| SETTLE-R05 v2 | 0.4 | pull request review: a failed claim lookup falls back to `10000` like a missing claim (`findClaim` swallows the exception); a failed policy lookup does not |
| SETTLE-R09 v2 | 0.3 | pull request review: the arithmetic is `double` end to end and rounding is `Math.round(amount * 100.0) / 100.0`, not `BigDecimal` `HALF_UP`; decision: keep the legacy behaviour (`1.005` settles at `1.00`), not fix it; QUIRK-01, QUIRK-08 |
| SETTLE-R06 | 0.5 | superseded by SETTLE-R06 v2 |
| SETTLE-R06 v2 | 0.5 | parity review: any unhandled exception is screen `error`, HTTP 200, empty fields, empty errors (`errors.system` is page text, not an error key); decision: keep the legacy behaviour; QUIRK-05 |
| SETTLE-R10 | 0.5 | superseded by SETTLE-R10 v2 |
| SETTLE-R10 v2 | 0.5 | parity review: display is `String.format("%.2f", double)` like `FieldTag`, a separate half-up rounding of the decimal representation (`coveredAmount 1.01` beside `settlementAmount 1.00`); decision: keep the legacy behaviour; QUIRK-02 |
