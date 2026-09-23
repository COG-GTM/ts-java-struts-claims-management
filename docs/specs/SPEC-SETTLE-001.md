# SPEC-SETTLE-001 — Settlement calculation

Version: 0.1

Scope: the settlement calculate, save, and detail request flows of the claims
application. Every rule below is taken either from a recorded transcript
(`Observed:`) or from a source file (`Read:`). Rule ids are permanent: if a rule
changes, a new version of that id is added and the earlier text is kept.

## Inputs

**SETTLE-R01 (v1)** — A settlement is calculated by POSTing `claimId`,
`coveredAmount`, `deductible`, and `depreciation` to
`/claims/settlement/calculate.do`; the response status is 200 and the request is
forwarded to `/WEB-INF/jsp/settlement/calculate.jsp`.
Observed: settlement_calculate

**SETTLE-R02 (v1)** — `claimId` is parsed as an integer; when it is missing or
not parseable the value 119 is used.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**SETTLE-R03 (v1)** — `coveredAmount` is parsed as a double; when it is missing
or not parseable the value 5000 is used.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:32;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45

**SETTLE-R04 (v1)** — `depreciation` is parsed as a double; when it is missing or
not parseable the value 0 is used.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:33;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45

**SETTLE-R05 (v1)** — `deductible` has no numeric fallback. A null or empty
`deductible` is replaced by the string `"0"` by the action, and the calculator
independently treats a null or whitespace-only string as 0. A blank deductible
therefore yields `deductibleApplied` 0.00 and a successful calculation.
Observed: settlement_blank_deductible;
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:34-37;
src/main/java/com/northstar/claims/service/SettlementCalculator.java:26-29

**SETTLE-R06 (v1)** — A non-empty, non-numeric `deductible` is passed to
`Double.parseDouble` unguarded; the resulting exception is handled by the global
exception mapping and the request is forwarded to `/WEB-INF/jsp/error.jsp` with
status 200 and no validation errors.
Observed: settlement_bad_deductible;
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:28;
src/main/webapp/WEB-INF/struts-config.xml:59-60

**SETTLE-R07 (v1)** — The policy limit used by the calculation is the
`policy_limit` of the policy referenced by the claim's `policy_id`. Claim 119
references policy 9001 (limit 1000) and claim 120 references policy 9002
(limit 100000).
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:24-31;
src/main/java/com/northstar/claims/dao/PolicyDAO.java:173;
src/main/resources/db/schema.sql:21;
src/main/resources/db/seed.sql:49-50,253-254

**SETTLE-R08 (v1)** — On the calculate path, if the claim is not found, or the
claim's policy is not found, the policy limit falls back to 10000.
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:25-31

**SETTLE-R09 (v1)** — No Struts validation runs on the settlement actions
(`validate="false"`), so `validation_errors` is always empty on these paths.
Read: src/main/webapp/WEB-INF/struts-config.xml:206-222

## Calculation

**SETTLE-R10 (v1)** — Gross loss = `coveredAmount - depreciation`. With covered
5000.00 and depreciation 500.00 the gross is 4500.00, which becomes the
settlement when no deductible or cap applies.
Observed: settlement_blank_deductible;
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:30

**SETTLE-R11 (v1)** — Net amount = gross - deductible. With covered 5000.00,
depreciation 0.00 and deductible 500.00 the net is 4500.00 before the cap.
Observed: settlement_calculate;
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:31

**SETTLE-R12 (v1)** — The net amount is floored at zero: when the deductible
exceeds the gross loss the settlement is 0.00 and `cappedAtLimit` is false
(covered 1000.00, deductible 2000.00 gives settlement 0.00).
Observed: settlement_deductible_floor;
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:32-34

**SETTLE-R13 (v1)** — `cappedAtLimit` is true when the floored net amount is
strictly greater than the policy limit, and the settlement becomes the policy
limit. Claim 119 (limit 1000): net 4500.00 gives settlement 1000.00 capped, and
net 19900.00 (covered 20000.00, deductible 100.00) also gives 1000.00 capped.
Observed: settlement_calculate, settlement_policy_cap;
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:35-36

**SETTLE-R14 (v1)** — Order of operations is fixed: depreciation, then
deductible, then the zero floor, then the cap, then rounding.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:30-37

**SETTLE-R15 (v1)** — Rounding is performed by exactly one statement,
`double rounded = Math.round(amount * 100.0) / 100.0;`, applied to the capped
amount only. It is binary double arithmetic, not decimal.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:37

**SETTLE-R16 (v1)** — Under SETTLE-R15 a covered amount of 1.005 (no deductible,
no depreciation, under the limit) produces a settlement amount of 1.00, i.e. the
half cent rounds down.
Observed: settlement_half_cent;
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:37

**SETTLE-R17 (v1)** — Only the settlement amount is rounded. `coveredAmount`,
`deductibleApplied`, and `depreciation` are copied onto the result as supplied.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:38-43

**SETTLE-R18 (v1)** — The result carries `coveredAmount`, `deductibleApplied`,
`depreciation`, `cappedAtLimit`, and `settlementAmount`; the action then sets
`claimId` and places the result in request scope as `settlement`, with the
policy limit as `policyLimit`.
Read: src/main/java/com/northstar/claims/service/SettlementCalculator.java:38-45;
src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:38-42

## Output

**SETTLE-R19 (v1)** — Values on the settlement screens are written by the
`<ns:field>` tag as `<span id="f_NAME">value</span>`, with `&`, `<`, `>`, and `"`
escaped and a null value rendered as the empty string.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java:34-46,79-85

**SETTLE-R20 (v1)** — `type="money"` formats the value with `String.format("%.2f",
...)`, so amounts appear with exactly two decimals (for example settlement
1000.00, deductible 500.00).
Observed: settlement_calculate;
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-59

**SETTLE-R21 (v1)** — The display rounding of SETTLE-R20 is not the same as the
calculation rounding of SETTLE-R15: the covered amount 1.005 is displayed as
1.01 while the settlement amount computed from it is 1.00.
Observed: settlement_half_cent

**SETTLE-R22 (v1)** — A value that cannot be parsed as a number is printed
unchanged by `type="money"`. `cappedAtLimit` is declared as money on
calculate.jsp and therefore renders as `true` or `false`.
Observed: settlement_calculate, settlement_deductible_floor;
Read: src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp:26;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-58

**SETTLE-R23 (v1)** — The calculate screen shows `coveredAmount`,
`deductibleApplied`, `depreciation`, `cappedAtLimit`, and `settlementAmount`, and
links to `detail.do?claimId=...` and `save.do`.
Read: src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp:17-30

## Persistence

**SETTLE-R24 (v1)** — `/claims/settlement/calculate.do` writes nothing: no
SETTLEMENT row is created and the claim is untouched (claim 120 remains CLOSED
across a calculate request).
Observed: settlement_calculate, settlement_blank_deductible;
Read: src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:20-43

**SETTLE-R25 (v1)** — `/claims/settlement/save.do` takes the same four request
parameters and recomputes the settlement with the same calculator rather than
reusing a previously calculated result; claim 119 with covered 5000.00,
deductible 500.00, depreciation 0.00 saves 1000.00, the capped value of
SETTLE-R13.
Observed: settlement_save;
Read: src/main/java/com/northstar/claims/web/SettlementSaveAction.java:24-33

**SETTLE-R26 (v1)** — On the save path the claim and its policy are dereferenced
without a null check and there is no 10000 limit fallback, so a claimId with no
claim row raises an exception and reaches error.jsp per SETTLE-R06.
Read: src/main/java/com/northstar/claims/web/SettlementSaveAction.java:25-33;
src/main/webapp/WEB-INF/struts-config.xml:59-60

**SETTLE-R27 (v1)** — The saved row gets `settlement_id` = `max(settlement_id) + 1`
over the SETTLEMENT table, `claim_id` from the request, `calculated_by` from the
session `user` attribute, and the literal `calculated_date` `2019-04-01`.
Observed: settlement_save (savedBy supervisor);
Read: src/main/java/com/northstar/claims/web/SettlementSaveAction.java:34-38;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75

**SETTLE-R28 (v1)** — The insert writes all nine SETTLEMENT columns —
`settlement_id`, `claim_id`, `covered_amount`, `deductible_applied`,
`depreciation`, `capped_at_limit`, `settlement_amount`, `calculated_by`,
`calculated_date` — and stores the rounded settlement amount (1000.00 for claim
119).
Observed: settlement_save (settlement.claim.119.amount = 1000.00);
Read: src/main/java/com/northstar/claims/dao/SettlementDAO.java:118-137;
src/main/resources/db/schema.sql:92-104

**SETTLE-R29 (v1)** — Save forwards to `/WEB-INF/jsp/settlement/save.jsp` with
status 200; that page shows the settlement amount (from the `settlementAmount`
request attribute) and `savedBy` from the session user.
Observed: settlement_save;
Read: src/main/webapp/WEB-INF/struts-config.xml:214-222;
src/main/webapp/WEB-INF/jsp/settlement/save.jsp:19-22

**SETTLE-R30 (v1)** — `/claims/settlement/detail.do` reads, it does not compute:
it loads the settlement for `claimId` (default 119 per SETTLE-R02) with
`select * from SETTLEMENT where claim_id = ? order by settlement_id desc`,
taking the first row, and forwards to `/WEB-INF/jsp/settlement/detail.jsp`. When
the claim has no settlement row the request attribute `settlement` is null.
Read: src/main/java/com/northstar/claims/web/SettlementDetailAction.java:17-23;
src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-114;
src/main/webapp/WEB-INF/struts-config.xml:357-363

**SETTLE-R31 (v1)** — The detail screen shows the stored settlement id and claim
id as `type="integer"`, covered amount, deductible applied, depreciation and
settlement amount as `type="money"`, `cappedAtLimit` as `type="text"`,
`calculatedBy` as plain text, and `calculatedDate` as `type="date"` reformatted
as `yyyy-MM-dd`.
Read: src/main/webapp/WEB-INF/jsp/settlement/detail.jsp:10-26;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:60-75

## Rule history

| Rule | Version | Change |
| --- | --- | --- |
| SETTLE-R01 | v1 | Initial rule, version 0.1 |
| SETTLE-R02 | v1 | Initial rule, version 0.1 |
| SETTLE-R03 | v1 | Initial rule, version 0.1 |
| SETTLE-R04 | v1 | Initial rule, version 0.1 |
| SETTLE-R05 | v1 | Initial rule, version 0.1 |
| SETTLE-R06 | v1 | Initial rule, version 0.1 |
| SETTLE-R07 | v1 | Initial rule, version 0.1 |
| SETTLE-R08 | v1 | Initial rule, version 0.1 |
| SETTLE-R09 | v1 | Initial rule, version 0.1 |
| SETTLE-R10 | v1 | Initial rule, version 0.1 |
| SETTLE-R11 | v1 | Initial rule, version 0.1 |
| SETTLE-R12 | v1 | Initial rule, version 0.1 |
| SETTLE-R13 | v1 | Initial rule, version 0.1 |
| SETTLE-R14 | v1 | Initial rule, version 0.1 |
| SETTLE-R15 | v1 | Initial rule, version 0.1 |
| SETTLE-R16 | v1 | Initial rule, version 0.1 |
| SETTLE-R17 | v1 | Initial rule, version 0.1 |
| SETTLE-R18 | v1 | Initial rule, version 0.1 |
| SETTLE-R19 | v1 | Initial rule, version 0.1 |
| SETTLE-R20 | v1 | Initial rule, version 0.1 |
| SETTLE-R21 | v1 | Initial rule, version 0.1 |
| SETTLE-R22 | v1 | Initial rule, version 0.1 |
| SETTLE-R23 | v1 | Initial rule, version 0.1 |
| SETTLE-R24 | v1 | Initial rule, version 0.1 |
| SETTLE-R25 | v1 | Initial rule, version 0.1 |
| SETTLE-R26 | v1 | Initial rule, version 0.1 |
| SETTLE-R27 | v1 | Initial rule, version 0.1 |
| SETTLE-R28 | v1 | Initial rule, version 0.1 |
| SETTLE-R29 | v1 | Initial rule, version 0.1 |
| SETTLE-R30 | v1 | Initial rule, version 0.1 |
| SETTLE-R31 | v1 | Initial rule, version 0.1 |
