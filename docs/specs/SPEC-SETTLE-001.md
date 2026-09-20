# SPEC-SETTLE-001: settlement calculation

Version: 0.3
Status: draft, reviewed by the engineer, checked by parity replay
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
A missing or non-numeric `coveredAmount` is treated as `5000`; a missing or
non-numeric `depreciation` is treated as `0`. Read: `decimal(...)` calls in
`SettlementCalculateAction`.

### SETTLE-R04 Blank deductible
A blank or missing `deductible` is treated as `0.00` and shown as
`deductibleApplied 0.00`. Observed: `settlement_blank_deductible`,
`settlement_half_cent`.

### SETTLE-R05 Policy limit
The policy limit is the `policy_limit` of the policy attached to the claim.
If the claim does not exist, or its policy does not exist, the limit is
`10000` and the calculation still runs. Observed: `settlement_calculate`
(claim 119, policy 9001, limit 1000). Read: `SettlementCalculateAction`,
`double limit = 10000;` before the lookups; confirmed by a manual request with
`claimId=9999`, which returned `settlementAmount 90.00` for covered 100 and
deductible 10. Open question: OQ-01.

### SETTLE-R06 Non-numeric deductible
A `deductible` that is not a number ends in the system error screen
(`error.jsp`, HTTP 200, no business fields, no validation error markers).
Observed: `settlement_bad_deductible`. Quirk: QUIRK-05.

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
The settlement amount is rounded with `double` arithmetic,
`Math.round(amount * 100.0) / 100.0`. This is not decimal half-up rounding:
input `1.005` gives `1.00`, because the nearest `double` to `1.005` is below
it. Observed: `settlement_half_cent`. Quirk: QUIRK-01.

### SETTLE-R10 Display format
Money fields (`coveredAmount`, `deductibleApplied`, `depreciation`,
`settlementAmount`) are shown with `String.format("%.2f", double)`, which
rounds the decimal form half up, so the input `1.005` is echoed as
`coveredAmount 1.01` on the same screen that shows `settlementAmount 1.00`.
`cappedAtLimit` is shown as `true` or `false`. Observed: every settlement
transcript with a result screen. Quirk: QUIRK-02.

## Persistence

### SETTLE-R11 Calculate does not write
`POST /settlement/calculate.do` inserts nothing. Observed: `db_state` is
empty for every calculate transcript; Read: no DAO write in
`SettlementCalculateAction`.

### SETTLE-R12 Save
`POST /settlement/save.do` recalculates with the same rules, except that a
missing claim or policy is a system error rather than the `10000` fallback
(QUIRK-04). It inserts one
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
| SETTLE-R09, R10 | 0.3 | parity run showed `1.00` vs `1.01`; rule now names the `double` arithmetic and the separate display rounding; decision: keep legacy behaviour |
| SETTLE-R06, R12 | 0.3 | error screen carries no validation markers; save has no limit fallback |
