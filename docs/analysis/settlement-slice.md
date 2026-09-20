# Settlement slice: current-state analysis

Scope: the settlement calculation screen of the NorthStar Claims Struts 1.3.10
application. Every statement below cites the file it came from. Statements
marked "Observed" were confirmed by running the application on this machine
(`make seed`, `make run`, then requests against `http://localhost:8080/claims/`).
Statements marked "Read" come from reading code only.

## Request path

| Step | Where | Evidence |
| --- | --- | --- |
| Browser posts `claimId`, `coveredAmount`, `deductible`, `depreciation` to `/claims/settlement/calculate.do` | `struts-config.xml`, mapping `path="/settlement/calculate"` | Read |
| `AuthFilter` requires a logged-in session for every `*.do` request | `src/main/webapp/WEB-INF/web.xml`, `AuthFilter.java` | Observed: unauthenticated POST is redirected to `login.do` |
| Struts binds `settlementForm` (`SettlementForm`) but the action never reads it; it reads `request.getParameter` directly | `SettlementCalculateAction.java` lines 24 to 35 | Read |
| The action looks up the claim with `ClaimDAO.findById`, then the policy with `PolicyDAO.findById`, to get `policy_limit` | `SettlementCalculateAction.java`; `ClaimsActionSupport.findClaim` | Read |
| `SettlementCalculator.calculate` applies depreciation, deductible, floor, cap and rounding | `SettlementCalculator.java` lines 24 to 47 | Observed |
| The result is rendered by `calculate.jsp`; each money value passes through `FieldTag` with `type="money"` | `calculate.jsp`, `FieldTag.java` | Observed |
| `/settlement/save.do` recalculates, allocates `max(settlement_id)+1`, inserts into `SETTLEMENT` | `SettlementSaveAction.java`, `SettlementDAO.save` | Read |
| `/settlement/detail.do` shows the latest `SETTLEMENT` row for the claim | `SettlementDetailAction.java`, `SettlementDAO.findByClaim` | Read |

## Tables touched

`CLAIM` (read), `POLICY` (read), `SETTLEMENT` (read and insert). Schema in
`src/main/resources/db/schema.sql`. Seed rows that matter for the slice, from
`src/main/resources/db/seed.sql`:

| Row | Values | Why it matters |
| --- | --- | --- |
| `POLICY 9001` | `policy_limit = 1000`, `deductible = 100` | Low limit, so the standard calculation is capped |
| `POLICY 9002` | `policy_limit = 100000`, `deductible = 5000` | Used by claim 120 |
| `CLAIM 119` | policy 9001, status `DENIED` | Default claim when `claimId` is missing or not a number |
| `CLAIM 120` | policy 9002, status `CLOSED` | Used by the blank-deductible, half-cent and floor transcripts |
| `SETTLEMENT 119`, `SETTLEMENT 120` | seeded rows, `settlement_amount = 0` | Existing rows for the two claims; `save` adds a new row rather than updating |

## Behaviour observed against the running application

| Input | Output | Source |
| --- | --- | --- |
| claim 119, covered 5000.00, deductible 500.00, depreciation 0.00 | `settlementAmount 1000.00`, `cappedAtLimit true` | `transcripts/settlement_calculate.json` |
| claim 120, covered 5000.00, deductible blank, depreciation 500.00 | `deductibleApplied 0.00`, `settlementAmount 4500.00` | `transcripts/settlement_blank_deductible.json` |
| claim 120, covered 1000.00, deductible 2000.00 | `settlementAmount 0.00` | `transcripts/settlement_deductible_floor.json` |
| claim 119, covered 20000.00, deductible 100.00 | `settlementAmount 1000.00`, `cappedAtLimit true` | `transcripts/settlement_policy_cap.json` |
| claim 120, covered 1.005, deductible blank | `coveredAmount 1.01`, `settlementAmount 1.00` | `transcripts/settlement_half_cent.json` |
| claim 119, deductible `abc` | `Claims System Error` page, HTTP 200, no fields | `transcripts/settlement_bad_deductible.json` |
| claim 9999 (does not exist), covered 100, deductible 10 | `settlementAmount 90.00`, not capped | manual request; limit fell back to `10000` |

## Rounding

`SettlementCalculator.calculate` rounds with `Math.round(amount * 100.0) / 100.0`
on a `double`. For `1.005` the product is `100.49999999999999`, so the result
is `1.00`. The same page shows the covered amount as `1.01`, because `FieldTag`
formats with `String.format("%.2f", ...)`, which rounds the decimal
representation half up. Two rounding rules live on one screen. This is the
divergence a rewrite in `BigDecimal` with `HALF_UP` would introduce.

## Documentation checked against code

`docs/ARCHITECTURE.txt` says "Most actions extend WorkflowAction". No class
named `WorkflowAction` exists in `src/main/java`. Every settlement action
extends `ClaimsActionSupport`. The document is out of date on this point.

`README.md` says the build targets Java 7 source and target. `pom.xml` sets
`maven.compiler.source` and `target` to `1.7`, and the build on this machine
(JDK 11) prints `bootstrap class path not set in conjunction with -source 7`.
The claim holds.

## What is not visible from transcripts alone

* The `10000` policy limit fallback when the claim or policy is missing
  (`SettlementCalculateAction.java`, `double limit = 10000;`). No transcript
  exercises it.
* The `settlementForm` bean is declared in `struts-config.xml` and
  `validation.xml` has no rules for it, so no Struts validation runs on this
  screen.
* `SettlementService.calculateAndSave` hard-codes `calculatedBy = "supervisor"`
  and `calculatedDate = "2019-03-01"`, while `SettlementSaveAction` uses the
  session user and `2019-04-01`. Only the action path is reachable from the UI.
* The `db_state` probe `settlement.claim.119.amount` in
  `tools/capture/capture.py` calls `GET /settlement/calculate.do?claimId=119`,
  which recalculates from defaults. It does not read the saved row.
