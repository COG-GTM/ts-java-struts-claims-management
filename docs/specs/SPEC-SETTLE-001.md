# SPEC-SETTLE-001 — Settlement calculation

Version: 0.1

Scope: the settlement calculate, save and detail request paths of the NorthStar
claims web module, as shown by the `transcripts/settlement_*.json` transcripts
and the code they exercise.

Each rule carries its evidence: `Observed: <transcript name>` for behaviour taken
from a captured transcript, `Read: <file and line>` for behaviour taken from the
source. Rule identifiers are stable. When a rule changes, a new rule with the
same number and a `v2` suffix is added and the old text is left in place.

Reference data used by the transcripts: claim 119 belongs to policy 9001, whose
policy limit is 1000; claim 120 belongs to policy 9002, whose policy limit is
100000.
Read: src/main/resources/db/seed.sql lines 49-50, 253-254.

## Inputs

**SETTLE-R01** — `POST /claims/settlement/calculate.do` is handled by
`SettlementCalculateAction`, with form bean `settlementForm`, request scope and
`validate="false"`, and its single forward `settlement` targets
`/WEB-INF/jsp/settlement/calculate.jsp`. No Struts validation runs, so the
transcripts record no validation errors.
Read: src/main/webapp/WEB-INF/struts-config.xml lines 205-213.
Observed: settlement_calculate.

**SETTLE-R02** — The action reads its values from the raw request parameters
(`claimId`, `coveredAmount`, `depreciation`, `deductible`), not from the form
bean.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java
lines 23, 32-34.

**SETTLE-R03** — `claimId` is parsed with `integer(...)`, which returns the
fallback `119` when the parameter is absent or not an integer.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java
line 23; src/main/java/com/northstar/claims/web/ClaimsActionSupport.java
lines 31-37.

**SETTLE-R04** — `coveredAmount` is parsed with `decimal(...)` and falls back to
`5000`; `depreciation` is parsed the same way and falls back to `0`. The
fallback applies to an absent parameter and to any value `Double.parseDouble`
rejects.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java
lines 32-33; src/main/java/com/northstar/claims/web/ClaimsActionSupport.java
lines 39-45.

**SETTLE-R05** — The policy limit is the `policy_limit` of the policy of the
claim identified by `claimId`. If the claim cannot be loaded, or the policy row
is missing, the limit stays at the literal default `10000`.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java
lines 24-31.

**SETTLE-R06** — A missing or zero-length `deductible` parameter is replaced by
the string `"0"` before the calculator is called; the calculator additionally
treats a null or whitespace-only string as `0`. A blank deductible therefore
yields `deductibleApplied` `0.00`.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java
lines 34-37;
src/main/java/com/northstar/claims/service/SettlementCalculator.java
lines 26-29.
Observed: settlement_blank_deductible.

**SETTLE-R07** — A non-blank, non-numeric `deductible` makes
`Double.parseDouble` throw. The exception is not caught in the settlement path,
so the Struts global exception handler for `java.lang.Exception` forwards to
`/WEB-INF/jsp/error.jsp` with HTTP status 200 and no business fields.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java
line 28; src/main/webapp/WEB-INF/struts-config.xml lines 59-60.
Observed: settlement_bad_deductible.

## Calculation

**SETTLE-R08** — Gross loss is `coveredAmount - depreciation`.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java
line 30.
Observed: settlement_blank_deductible (5000.00 - 500.00 = 4500.00).

**SETTLE-R09** — The net amount is the gross loss minus the deductible value.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java
line 31.
Observed: settlement_calculate (5000.00 - 0.00 - 500.00 = 4500.00 before the
cap).

**SETTLE-R10** — The net amount is floored at zero: if it is negative it is set
to `0`. The deductible that produced the negative value is still reported in
full as `deductibleApplied`.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java
lines 32-34.
Observed: settlement_deductible_floor (covered 1000.00, deductible 2000.00 →
`settlementAmount` 0.00, `deductibleApplied` 2000.00).

**SETTLE-R11** — The cap flag is `netAmount > policyLimit`, evaluated strictly
after the zero floor. When the flag is true the settlement amount becomes the
policy limit; otherwise it stays the net amount.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java
lines 35-36.
Observed: settlement_calculate (net 4500.00 against limit 1000 →
`cappedAtLimit` true, amount 1000.00); settlement_policy_cap (net 19900.00
against limit 1000 → `cappedAtLimit` true, amount 1000.00);
settlement_blank_deductible (net 4500.00 against limit 100000 →
`cappedAtLimit` false).

**SETTLE-R12** — The capped amount is rounded to cents by exactly one
statement, `double rounded = Math.round(amount * 100.0) / 100.0;`. This is
binary-`double` arithmetic: for `1.005` the product `1.005 * 100.0` is
`100.49999999999999`, `Math.round` gives `100`, and the result is `1.0`, so the
settlement amount for a covered amount of `1.005` is `1.00`, not `1.01`.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java
line 37.
Observed: settlement_half_cent.

**SETTLE-R13** — Only the settlement amount is rounded. `coveredAmount`,
`deductibleApplied` and `depreciation` are stored on the result exactly as
supplied, and the cap flag as computed.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java
lines 38-43.

## Output

**SETTLE-R14** — The calculate action puts the result in request attribute
`settlement` (with `claimId` set on it), the limit in `policyLimit` and
`screenName` `detail`, then returns the `settlement` forward, so the transcript
result is `forward:/WEB-INF/jsp/settlement/calculate.jsp` with status 200.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java
lines 38-42.
Observed: settlement_calculate.

**SETTLE-R15** — Each displayed value is written by `<ns:field>` as
`<span id="f_NAME">TEXT</span>`, with `&`, `<`, `>` and `"` escaped in both the
name and the text. These spans are what the transcripts record as
`business_fields`.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java lines 37-41,
79-85.

**SETTLE-R16** — The calculate screen renders five fields: `coveredAmount`,
`deductibleApplied`, `depreciation`, `cappedAtLimit` and `settlementAmount`, all
declared with `type="money"`.
Read: src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp lines 20, 22, 24, 26,
28.
Observed: settlement_calculate.

**SETTLE-R17** — `type="money"` formats with
`String.format("%.2f", Double.parseDouble(source))`. This formatting rounds
half-up on the shortest decimal representation of the double, which differs
from SETTLE-R12: a covered amount of `1.005` is displayed as `1.01` while the
settlement amount computed from it is displayed as `1.00`.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java lines 52-55.
Observed: settlement_half_cent (`coveredAmount` 1.01, `settlementAmount` 1.00).

**SETTLE-R18** — When a `money` value cannot be parsed as a number, the tag
returns the source text unchanged. `cappedAtLimit` is declared `money` but
carries a boolean, so it is displayed as `true` or `false`.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java lines 56-58;
src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp line 26.
Observed: settlement_calculate (`cappedAtLimit` true);
settlement_deductible_floor (`cappedAtLimit` false).

**SETTLE-R19** — A null value renders as the empty string, and `type` defaults
to `text` when unset or empty; `text` values are emitted unchanged.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java lines 25-31,
48-51, 76.

## Persistence

**SETTLE-R20** — `POST /claims/settlement/save.do` is handled by
`SettlementSaveAction`, with form bean `settlementForm`, request scope and
`validate="false"`, and its `settlement` forward targets
`/WEB-INF/jsp/settlement/save.jsp`.
Read: src/main/webapp/WEB-INF/struts-config.xml lines 214-222.
Observed: settlement_save.

**SETTLE-R21** — The save action does not reuse a previously calculated
settlement. It re-reads the same parameters with the same fallbacks as
SETTLE-R03, SETTLE-R04 and SETTLE-R06 and recalculates, using the limit of the
policy of the claim. Unlike the calculate action it does not guard against a
missing claim or policy, so it has no `10000` default.
Read: src/main/java/com/northstar/claims/web/SettlementSaveAction.java
lines 24-33.

**SETTLE-R22** — Before insert, the record is given a settlement id from
`nextId("SETTLEMENT")`, which is
`select coalesce(max(settlement_id),0)+1 from SETTLEMENT`; the claim id from the
request; `calculatedBy` from the session attribute `user` via
`String.valueOf(...)`; and the literal `calculatedDate` `2019-04-01`.
Read: src/main/java/com/northstar/claims/web/SettlementSaveAction.java
lines 34-38; src/main/java/com/northstar/claims/web/ClaimsActionSupport.java
lines 61-75.

**SETTLE-R23** — The row is inserted into `SETTLEMENT` with the columns
`settlement_id, claim_id, covered_amount, deductible_applied, depreciation,
capped_at_limit, settlement_amount, calculated_by, calculated_date`, using a
prepared statement; the table declares all of these `NOT NULL`, with
`capped_at_limit` boolean, the amounts `DOUBLE`, `calculated_date` a SQL `DATE`,
and a foreign key to `CLAIM`.
Read: src/main/java/com/northstar/claims/dao/SettlementDAO.java lines 118-137;
src/main/resources/db/schema.sql lines 92-104.

**SETTLE-R24** — After the insert the action exposes the settlement in request
attribute `settlement`, the amount separately in `settlementAmount`, sets
`screenName` to `detail`, and forwards to the save screen with status 200.
Read: src/main/java/com/northstar/claims/web/SettlementSaveAction.java
lines 40-44.
Observed: settlement_save.

**SETTLE-R25** — The save screen shows two fields: `settlementAmount` from the
request attribute as `money`, and `savedBy` from `${sessionScope.user}` as
`text`. `savedBy` is the current session user, not a column read back from the
database.
Read: src/main/webapp/WEB-INF/jsp/settlement/save.jsp lines 19, 21-22.
Observed: settlement_save (`settlementAmount` 1000.00, `savedBy` supervisor).

**SETTLE-R26** — The settlement_save transcript records
`settlement.claim.119.amount` as `1000.00`. This probe is captured by requesting
`/settlement/calculate.do?claimId=119` and reading the `settlementAmount` span,
so it reflects a recalculation with the parameter fallbacks of SETTLE-R04 and
SETTLE-R06, not a direct read of the stored row.
Read: tools/capture/capture.py lines 52-56, 186-187.
Observed: settlement_save.

**SETTLE-R27** — `/claims/settlement/detail.do` is handled by
`SettlementDetailAction` with `validate="false"` and forwards
`settlementDetail` to `/WEB-INF/jsp/settlement/detail.jsp`. It parses `claimId`
with the same `119` fallback, loads the settlement through
`SettlementDAO.findByClaim`, and sets `settlement`, `screenName` `detail`,
`screenMode` `read` and `operatorScope` `claims`.
Read: src/main/java/com/northstar/claims/web/SettlementDetailAction.java
lines 17-23; src/main/webapp/WEB-INF/struts-config.xml lines 356-363.

**SETTLE-R28** — `findByClaim` selects `from SETTLEMENT where claim_id = ?
order by settlement_id desc` and returns the first row, i.e. the highest
settlement id for that claim, or null when the claim has no settlement.
Read: src/main/java/com/northstar/claims/dao/SettlementDAO.java lines 100-115.

**SETTLE-R29** — The detail screen renders `detailSettlementId` and
`detailClaimId` as `integer`, `detailCoveredAmount`, `detailDeductible`,
`detailDepreciation` and `detailAmount` as `money`, `detailCapped` as `text`,
and `detailDate` as `date`. The `integer` format truncates toward zero through
`(long) Double.parseDouble(source)`, and the `date` format re-formats a
`yyyy-MM-dd` value as `yyyy-MM-dd`, returning the source unchanged when either
parse fails.
Read: src/main/webapp/WEB-INF/jsp/settlement/detail.jsp lines 10, 12, 14, 16,
18, 20, 22, 26; src/main/java/com/northstar/claims/web/tag/FieldTag.java
lines 60-75.

## Rule history

| Rule | Version | Change |
| --- | --- | --- |
| SETTLE-R01 | 0.1 | Initial rule. |
| SETTLE-R02 | 0.1 | Initial rule. |
| SETTLE-R03 | 0.1 | Initial rule. |
| SETTLE-R04 | 0.1 | Initial rule. |
| SETTLE-R05 | 0.1 | Initial rule. |
| SETTLE-R06 | 0.1 | Initial rule. |
| SETTLE-R07 | 0.1 | Initial rule. |
| SETTLE-R08 | 0.1 | Initial rule. |
| SETTLE-R09 | 0.1 | Initial rule. |
| SETTLE-R10 | 0.1 | Initial rule. |
| SETTLE-R11 | 0.1 | Initial rule. |
| SETTLE-R12 | 0.1 | Initial rule. |
| SETTLE-R13 | 0.1 | Initial rule. |
| SETTLE-R14 | 0.1 | Initial rule. |
| SETTLE-R15 | 0.1 | Initial rule. |
| SETTLE-R16 | 0.1 | Initial rule. |
| SETTLE-R17 | 0.1 | Initial rule. |
| SETTLE-R18 | 0.1 | Initial rule. |
| SETTLE-R19 | 0.1 | Initial rule. |
| SETTLE-R20 | 0.1 | Initial rule. |
| SETTLE-R21 | 0.1 | Initial rule. |
| SETTLE-R22 | 0.1 | Initial rule. |
| SETTLE-R23 | 0.1 | Initial rule. |
| SETTLE-R24 | 0.1 | Initial rule. |
| SETTLE-R25 | 0.1 | Initial rule. |
| SETTLE-R26 | 0.1 | Initial rule. |
| SETTLE-R27 | 0.1 | Initial rule. |
| SETTLE-R28 | 0.1 | Initial rule. |
| SETTLE-R29 | 0.1 | Initial rule. |
