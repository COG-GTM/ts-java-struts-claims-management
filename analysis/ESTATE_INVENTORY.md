# Struts estate inventory — COG-GTM/ts-java-struts-claims-management

Built by reading `src/main/webapp/WEB-INF/*.xml`, `src/main/webapp/WEB-INF/jsp/**`, and
`src/main/java/com/northstar/claims/**`. `docs/ARCHITECTURE.txt` was not used.

Module wiring (`WEB-INF/web.xml`): `ActionServlet` is mapped to `*.do`; the default module loads
`struts-config.xml` + `struts-config-claims.xml`, and the `/admin` module loads
`struts-config-admin.xml` via the `config/admin` init-param. `AuthFilter` is mapped to `*.do`.

## 1. Action mappings

### 1.1 struts-config.xml (default module, no prefix)

| Path | Action class | Form bean | validate | Forwards (name → path) |
|---|---|---|---|---|
| /login | web.LoginAction | loginForm | false | login → jsp/login.jsp; home → jsp/home.jsp |
| /home | web.HomeAction | — | false | home → jsp/home.jsp |
| /logout | web.LogoutAction | — | false | *(none; globals only)* |
| /policy/search | web.PolicySearchAction | policySearchForm | false | policySearch → jsp/policy/search.jsp |
| /policy/view | web.PolicyViewAction | policyViewForm | false | policyView → jsp/policy/view.jsp |
| /policy/list | web.PolicyListAction | — | false | policyList → jsp/policy/list.jsp |
| /policy/coverages | web.PolicyCoveragesAction | — | false | coverages → jsp/policy/coverages.jsp |
| /policy/coverageDetail | web.PolicyCoverageDetailAction | — | false | coverageDetail → jsp/policy/coverageDetail.jsp |
| /policy/insuredParty | web.PolicyInsuredPartyAction | — | false | insuredParty → jsp/policy/insuredParty.jsp |
| /policy/renewal | web.PolicyRenewalAction | — | false | renewal → jsp/policy/renewal.jsp |
| /intake/new | web.IntakeNewAction | intakeForm | false | intake → jsp/intake/new.jsp |
| /intake/submit | web.IntakeSubmitAction | intakeForm | false (input=jsp/intake/new.jsp) | input → jsp/intake/new.jsp; confirm → jsp/intake/confirm.jsp |
| /intake/confirm | web.IntakeConfirmAction | — | false | confirm → jsp/intake/confirm.jsp |
| /workbench/list | web.WorkbenchListAction | workbenchForm | false | workbenchList → jsp/workbench/list.jsp |
| /workbench/view | web.WorkbenchViewAction | workbenchForm | false | workbenchView → jsp/workbench/view.jsp |
| /workbench/assign | web.WorkbenchAssignAction | — | false | workbenchView → jsp/workbench/view.jsp |
| /workbench/status | web.WorkbenchStatusAction | statusDyn (DynaValidatorForm) | false | workbenchView → jsp/workbench/view.jsp |
| /workbench/reserve | web.WorkbenchReserveAction | amountDyn (DynaValidatorForm) | false | workbenchView → jsp/workbench/view.jsp |
| /workbench/note | web.WorkbenchNoteAction | — | false | workbenchView → jsp/workbench/view.jsp |
| /workbench/notes | web.WorkbenchNoteHistoryAction | — | false | notes → jsp/workbench/notes.jsp |
| /workbench/reserveHistory | web.WorkbenchReserveHistoryAction | — | false | reserveHistory → jsp/workbench/reserveHistory.jsp |
| /workbench/statusHistory | web.WorkbenchStatusHistoryAction | — | false | statusHistory → jsp/workbench/statusHistory.jsp |
| /settlement/calculate | web.SettlementCalculateAction | settlementForm | false | settlement → jsp/settlement/calculate.jsp |
| /settlement/save | web.SettlementSaveAction | settlementForm | false | settlement → jsp/settlement/save.jsp |
| /settlement/detail | web.SettlementDetailAction | — | false | settlementDetail → jsp/settlement/detail.jsp |
| /payment/issue | web.PaymentIssueAction | paymentForm | false | payment → jsp/payment/issue.jsp |
| /payment/history | web.PaymentHistoryAction | — | false | payment → jsp/payment/history.jsp |
| /payment/detail | web.PaymentDetailAction | — | false | paymentDetail → jsp/payment/detail.jsp |
| /payment/remittance | web.PaymentRemittanceAction | — | false | remittance → jsp/payment/remittance.jsp |
| /report/index | web.ReportIndexAction | — | false | reportIndex → jsp/report/index.jsp |
| /report/openByAdjuster | web.OpenReportAction | — | false | report → jsp/report/openByAdjuster.jsp |
| /report/lossRatio | web.LossRatioReportAction | — | false | report → jsp/report/lossRatio.jsp |
| /report/agedClaims | web.AgedClaimsReportAction | — | false | report → jsp/report/agedClaims.jsp |
| /report/adjusterWorkload | web.AdjusterWorkloadDetailAction | — | false | adjusterWorkload → jsp/report/adjusterWorkload.jsp |
| /report/claimAgingDetail | web.ClaimAgingDetailAction | — | false | claimAgingDetail → jsp/report/claimAgingDetail.jsp |
| /report/reconciliation | web.ReconciliationAction | — | false | reconciliation → jsp/report/reconciliation.jsp |
| /report/premiumDetail | web.PremiumDetailAction | — | false | premiumDetail → jsp/report/premiumDetail.jsp |

All class names are under `com.northstar.claims.` (shown as `web.X`). Every path in the file has
`validate="false"`, so the Validator plug-in and `validation.xml` (rules for `intakeForm`,
`statusDyn`, `searchDyn`, `amountDyn`) never run; `IntakeSubmitAction` hand-rolls the same three
checks in Java.

Global forwards: `login` → jsp/login.jsp, `home` → jsp/home.jsp, `intakeInput` → jsp/intake/new.jsp
(unused by any action), `error` → jsp/error.jsp. Global exception: `java.lang.Exception` →
jsp/error.jsp.

Form beans declared: loginForm, policySearchForm, policyViewForm, intakeForm, workbenchForm,
settlementForm, paymentForm, deadForm, searchDyn, statusDyn, amountDyn. `deadForm` and `searchDyn`
are declared but referenced by no mapping.

### 1.2 struts-config-claims.xml (second descriptor of the default module)

Empty `<action-mappings/>`. It contributes a display-name/description only — no paths, no forms.

### 1.3 struts-config-admin.xml (module prefix `/admin`)

| Path | Action class | Form bean | validate | Forwards |
|---|---|---|---|---|
| /adjusters | web.admin.AdjusterAdminAction | adjusterForm | false | adjusters → jsp/admin/adjusters.jsp; editAdjuster → jsp/admin/editAdjuster.jsp |
| /reference | web.admin.ReferenceDataAction | — | false | reference → jsp/admin/reference.jsp |

`adjusterForm` is named by the mapping but declared in no `<form-beans>` section in any descriptor,
and the module declares no message-resources problem beyond reusing `ApplicationResources`. There is
no `/admin/editAdjuster` mapping even though `adjusters.jsp` links to `editAdjuster.do`.

## 2. JSPs and the actions they target

"Rendered by" = mapping whose forward points at the page. "Posts/links to" lists only links written
in that file; every page also inherits the nav bar (`/policy/list.do`, `/workbench/list.do`,
`/report/openByAdjuster.do`, `/logout.do`, plus from `nav.jsp` `/intake/new.do`,
`/payment/history.do`, `/policy/search.do`).

| JSP | Rendered by | Posts / links to |
|---|---|---|
| login.jsp | /login (global `login`) | POST `<html:form action="/login.do">` |
| home.jsp | /home, /login (`home`) | policy/list.do, workbench/list.do, report/index.do |
| error.jsp | global exception, web.xml 404 | login.do |
| header.jsp / nav.jsp / footer.jsp | included fragments (no mapping) | nav bar links above, home.do |
| policy/search.jsp | /policy/search | POST `<html:form action="/policy/search.do">` |
| policy/list.jsp | /policy/list | view.do?policyId=… |
| policy/view.jsp | /policy/view | coverages.do, coverageDetail.do, insuredParty.do, renewal.do (all ?policyId=…) |
| policy/coverages.jsp | /policy/coverages | nav only |
| policy/coverageDetail.jsp | /policy/coverageDetail | nav only |
| policy/insuredParty.jsp | /policy/insuredParty | nav only |
| policy/renewal.jsp | /policy/renewal | nav only |
| intake/new.jsp | /intake/new, /intake/submit (`input`) | POST `<form action="intake/submit.do">` — browser-relative to `/claims/intake/`, so it resolves to `/claims/intake/intake/submit.do`, not the mapping |
| intake/confirm.jsp | /intake/submit (`confirm`), /intake/confirm | ../workbench/view.do?claimId=… |
| workbench/list.jsp | /workbench/list | view.do?claimId=… |
| workbench/view.jsp | /workbench/view, /workbench/{assign,status,reserve,note} | assign.do, reserve.do, status.do?status=INVESTIGATING, note.do, notes.do, reserveHistory.do, statusHistory.do |
| workbench/notes.jsp | /workbench/notes | nav only |
| workbench/reserveHistory.jsp | /workbench/reserveHistory | nav only |
| workbench/statusHistory.jsp | /workbench/statusHistory | nav only |
| workbench/note.jsp | **no mapping forwards here** | POST `<html:form action="/workbench/note.do">` |
| workbench/assignment.jsp | **no mapping forwards here** | POST `<form action="assign.do">` |
| settlement/calculate.jsp | /settlement/calculate | detail.do?claimId=…, save.do |
| settlement/save.jsp | /settlement/save | nav only |
| settlement/detail.jsp | /settlement/detail | nav only |
| payment/issue.jsp | /payment/issue | nav only |
| payment/history.jsp | /payment/history | detail.do?paymentId=61 (hard-coded), remittance.do?claimId=… |
| payment/detail.jsp | /payment/detail | nav only |
| payment/remittance.jsp | /payment/remittance | nav only |
| report/index.jsp | /report/index | openByAdjuster.do, lossRatio.do, agedClaims.do, adjusterWorkload.do, claimAgingDetail.do, reconciliation.do, premiumDetail.do |
| report/openByAdjuster.jsp | /report/openByAdjuster | nav only |
| report/lossRatio.jsp | /report/lossRatio | nav only |
| report/agedClaims.jsp | /report/agedClaims | nav only |
| report/adjusterWorkload.jsp | /report/adjusterWorkload | nav only |
| report/claimAgingDetail.jsp | /report/claimAgingDetail | nav only |
| report/reconciliation.jsp | /report/reconciliation | nav only |
| report/premiumDetail.jsp | /report/premiumDetail | nav only |
| admin/adjusters.jsp | /admin/adjusters | editAdjuster.do (no such mapping) |
| admin/editAdjuster.jsp | /admin/adjusters (`editAdjuster`) | POST `<html:form action="/admin/adjusters.do">` |
| admin/reference.jsp | /admin/reference | nav only |

`workbench/note.jsp` and `workbench/assignment.jsp` are orphans: `/workbench/note` and
`/workbench/assign` both forward to `workbench/view.jsp`, so neither page can be reached.

## 3. SQL in com.northstar.claims.dao

| DAO.method | Statement | Tables |
|---|---|---|
| AdjusterDAO.findById | `select * from ADJUSTER where adjuster_id = ?` | ADJUSTER |
| AdjusterDAO.findAll | `select * from ADJUSTER order by adjuster_id` | ADJUSTER |
| AdjusterDAO.count | `select count(*) from ADJUSTER` | ADJUSTER |
| AdjusterDAO.delete | `delete from ADJUSTER where adjuster_id = ?` | ADJUSTER |
| AdjusterDAO.authenticate | `select * from ADJUSTER where username = ? and password = ? and active = true` | ADJUSTER |
| AdjusterDAO.findActive | `select * from ADJUSTER where active = true order by full_name` | ADJUSTER |
| ClaimDAO.findById | `select * from CLAIM where claim_id = ?` | CLAIM |
| ClaimDAO.findAll | `select * from CLAIM order by claim_id` | CLAIM |
| ClaimDAO.count | `select count(*) from CLAIM` | CLAIM |
| ClaimDAO.delete | `delete from CLAIM where claim_id = ?` | CLAIM |
| ClaimDAO.findByStatus | `select * from CLAIM where status = ? order by claim_id` | CLAIM |
| ClaimDAO.search | `select * from CLAIM where description like '%<term>%' [and status = '<status>']` — string-concatenated, injectable | CLAIM |
| ClaimDAO.updateReserve | `update CLAIM set reserve_amount = ? where claim_id = ?` | CLAIM |
| ClaimDAO.insert | `insert into CLAIM (claim_id,claim_number,policy_id,claimant_name,status,reserve_amount) values (?,?,?,?,?,?)` | CLAIM |
| ClaimDAO.updateText (used by assign, updateStatus) | `update CLAIM set <column> = ? where claim_id = ?` — column name concatenated | CLAIM |
| NoteDAO.findById | `select * from CLAIM_NOTE where note_id = ?` | CLAIM_NOTE |
| NoteDAO.findAll | `select * from CLAIM_NOTE order by note_id` | CLAIM_NOTE |
| NoteDAO.count | `select count(*) from CLAIM_NOTE` | CLAIM_NOTE |
| NoteDAO.delete | `delete from CLAIM_NOTE where note_id = ?` | CLAIM_NOTE |
| NoteDAO.findByClaim | `select * from CLAIM_NOTE where claim_id = ? order by note_date` | CLAIM_NOTE |
| NoteDAO.insert | `insert into CLAIM_NOTE values (?,?,?,?,?)` — positional, no column list | CLAIM_NOTE |
| PaymentDAO.findById | `select * from PAYMENT where payment_id = ?` | PAYMENT |
| PaymentDAO.findAll | `select * from PAYMENT order by payment_id` | PAYMENT |
| PaymentDAO.count | `select count(*) from PAYMENT` | PAYMENT |
| PaymentDAO.delete | `delete from PAYMENT where payment_id = ?` | PAYMENT |
| PaymentDAO.findByClaim | `select * from PAYMENT where claim_id = ? order by payment_id` | PAYMENT |
| PaymentDAO.totalIssued | `select coalesce(sum(amount),0) from PAYMENT where claim_id = ?` | PAYMENT |
| PaymentDAO.insert | `insert into PAYMENT (payment_id,claim_id,settlement_id,payee_name,amount,payment_method,check_number,issued_date,status) values (?,?,?,?,?,?,?,?,?)` | PAYMENT (FK to CLAIM, SETTLEMENT) |
| PolicyDAO.findById | `select * from POLICY where policy_id = ?` | POLICY |
| PolicyDAO.findAll | `select * from POLICY order by policy_id` | POLICY |
| PolicyDAO.count | `select count(*) from POLICY` | POLICY |
| PolicyDAO.delete | `delete from POLICY where policy_id = ?` | POLICY |
| PolicyDAO.findByLine | `select * from POLICY where line_of_business = '<line>' order by policy_number` — concatenated, injectable | POLICY |
| PolicyDAO.search | `select * from POLICY where policy_number like ? or insured_name like ?` | POLICY |
| PolicyDAO.updateStatus | `update POLICY set status = ? where policy_id = ?` | POLICY |
| ReportDAO.openClaimsByAdjuster | `select assigned_adjuster, count(*) open_count, coalesce(sum(reserve_amount),0) reserve_total from CLAIM where status in ('OPEN','INVESTIGATING') group by assigned_adjuster order by assigned_adjuster` | CLAIM |
| ReportDAO.lossRatioByLine | `select p.line_of_business, sum(p.annual_premium), coalesce(sum(c.reserve_amount),0), coalesce(sum(pay.amount),0) from POLICY p left join CLAIM c on p.policy_id = c.policy_id left join PAYMENT pay on c.claim_id = pay.claim_id group by p.line_of_business` | POLICY, CLAIM, PAYMENT |
| ReportDAO.agedClaims | `select case when datediff('day', reported_date, date '<report.asof.date>') … end as age_bucket, count(*), coalesce(sum(reserve_amount),0) from CLAIM where status not in ('CLOSED','DENIED') group by …` — as-of date concatenated from `ClaimsConfig` | CLAIM |
| ReportDAO.claimCounts | `select status, count(*) from CLAIM group by status order by <orderBy>` — order-by concatenated | CLAIM |
| SettlementDAO.findById | `select * from SETTLEMENT where settlement_id = ?` | SETTLEMENT |
| SettlementDAO.findAll | `select * from SETTLEMENT order by settlement_id` | SETTLEMENT |
| SettlementDAO.count | `select count(*) from SETTLEMENT` | SETTLEMENT |
| SettlementDAO.delete | `delete from SETTLEMENT where settlement_id = ?` | SETTLEMENT |
| SettlementDAO.findByClaim | `select * from SETTLEMENT where claim_id = ? order by settlement_id desc` | SETTLEMENT |
| SettlementDAO.save | `insert into SETTLEMENT (settlement_id,claim_id,covered_amount,deductible_applied,depreciation,capped_at_limit,settlement_amount,calculated_by,calculated_date) values (?,…)` | SETTLEMENT (FK to CLAIM) |

### 3.1 SQL that is *not* in the DAO package (matters for extraction)

`com.northstar.claims.web.ClaimsActionSupport` opens its own `DriverManager` connection
(`jdbc:hsqldb:file:${claims.db.path}`, bypassing `ConnectionPool`/JNDI) and exposes generic
`nextId(table)` (`select coalesce(max(<t>_id),0)+1 from <t>`), `update(sql)`, `selectString`,
`selectStrings`, `countRows`, `selectAmount`. Actions use it to write directly:

- `WorkbenchAssignAction`: `update CLAIM set assigned_adjuster = '<param>' where claim_id = <param>` (concatenated)
- `WorkbenchStatusAction`: `update CLAIM set status = '<param>' …` (concatenated)
- `WorkbenchReserveAction`: `update CLAIM set reserve_amount = <param> …` (concatenated)
- `IntakeSubmitAction`: `insert into CLAIM values (?,?,9001,?,?,?,?,?,'OPEN',0,'adjuster1','supervisor',?)` — positional insert with hard-coded policy 9001 and adjuster
- `PaymentIssueAction`, `SettlementSaveAction`: `nextId("PAYMENT")` / `nextId("SETTLEMENT")` for key generation

So CLAIM is written from two places (DAO and actions) with different transaction and connection
handling. `WorkbenchNoteAction` writes nothing at all — `NoteDAO.insert` has no caller, so notes
submitted on the workbench are never persisted.

### 3.2 Tables

Schema (`src/main/resources/db/schema.sql`): POLICY, INSURED_PARTY, COVERAGE, CLAIM, CLAIM_NOTE,
RESERVE_HISTORY, SETTLEMENT, PAYMENT, ADJUSTER. No Java code reads INSURED_PARTY, COVERAGE or
RESERVE_HISTORY — the coverage/insured/reserve-history screens build their data from POLICY/CLAIM
rows or from in-memory lists (`PolicyCoveragesAction` always sets an empty `coverages` list), and
only `util.DatabaseDump` names those tables.

### 3.3 Dead or unreachable code paths found while tracing

- `service.ClaimManager`, `service.PolicyManager`, `service.SettlementService` have no callers in
  the web layer, so `ClaimDAO.search/assign/updateStatus/updateReserve`, `PolicyDAO.search/updateStatus`
  are reachable only through that dead layer.
- `AdjusterDAO.authenticate` and `findActive` are unused: `LoginAction` hard-codes credentials
  (`supervisor/supervisor`, `adjuster*/legacy*`) and never touches the database.
- `NoteDAO.insert`/`findUnused`, `ReportDAO.claimCounts`, every `delete(...)` are uncalled.

## 4. Candidate modules by actual coupling

| Module | Actions | JSPs | DAOs / services | Tables |
|---|---|---|---|---|
| **Reporting (read-only)** | /report/index, openByAdjuster, lossRatio, agedClaims, adjusterWorkload, claimAgingDetail, reconciliation, premiumDetail | report/*.jsp (8) | ReportDAO (+ ClaimDAO.findByStatus, PolicyDAO.findAll for two detail screens) | CLAIM, POLICY, PAYMENT (read) |
| **Policy** | /policy/search, view, list, coverages, coverageDetail, insuredParty, renewal | policy/*.jsp (7) | PolicyDAO, (PolicyManager, dead) | POLICY (read; updateStatus unused) |
| **Claim intake (FNOL)** | /intake/new, submit, confirm | intake/new.jsp, intake/confirm.jsp | inline SQL in IntakeSubmitAction + ClaimsActionSupport.nextId | CLAIM (write) |
| **Workbench (claim lifecycle)** | /workbench/list, view, assign, status, reserve, note, notes, reserveHistory, statusHistory | workbench/*.jsp (7, two orphaned) | ClaimDAO, NoteDAO, inline updates in ClaimsActionSupport | CLAIM (read/write), CLAIM_NOTE (read) |
| **Settlement** | /settlement/calculate, save, detail | settlement/*.jsp (3) | SettlementDAO, SettlementCalculator, PolicyDAO.findById, ClaimDAO.findById | SETTLEMENT (write), POLICY + CLAIM (read) |
| **Payment** | /payment/issue, history, detail, remittance | payment/*.jsp (4) | PaymentDAO, SettlementDAO.findByClaim | PAYMENT (write), SETTLEMENT (read) |
| **Admin / reference** | /admin/adjusters, /admin/reference | admin/*.jsp (3) | AdjusterDAO.findAll (reference data is a hard-coded list) | ADJUSTER (read) |
| **Platform (shared, not extractable)** | /login, /logout, /home, AuthFilter, ClaimsActionSupport, ConnectionPool | login.jsp, home.jsp, error.jsp, header/nav/footer.jsp | — | — |

Coupling that crosses those lines:

- Settlement → Payment: `PaymentIssueAction` calls `SettlementDAO.findByClaim` and stores
  `settlement_id` on the payment row; payment cannot be split from settlement without an interface.
- Settlement → Policy + Claim: the calculator needs `policy_limit` and the claim's `policy_id`.
- Reporting → everything, but only through `select`s in `ReportDAO` (plus two actions that reuse
  `ClaimDAO`/`PolicyDAO` finders). No writes, no shared mutable state.
- Workbench → Intake: intake's confirm page links into `workbench/view.do`, and both write CLAIM.
- Everything → `ClaimsActionSupport`, which is both the shared base class and a second, ad-hoc data
  access path.

## 5. Cleanest first extraction: Reporting

**Reporting** (`/report/*`, the 8 report JSPs, `ReportDAO`) is the lowest-risk first slice:

1. It is read-only. All four `ReportDAO` methods are `select`s, and none of the report actions
   write, so extraction cannot create dual-write or transaction-boundary problems — the hard part of
   moving Workbench, Intake, Settlement or Payment, which all write CLAIM/SETTLEMENT/PAYMENT from
   two different connection paths.
2. Its inbound coupling is a single entry point. Only `report/index.jsp` and the nav bar link into
   `/report/...`; no other module's action forwards to a report JSP and no action outside
   `web/*Report*`/`ReconciliationAction`/`PremiumDetailAction`/`AdjusterWorkloadDetailAction` calls
   `ReportDAO`. The seam is one link target, not a call graph.
3. Its outbound coupling is SQL-only. Reporting touches CLAIM, POLICY and PAYMENT purely through
   aggregate queries, so the extracted service can read a replica or a view; nothing in the
   remaining monolith depends on reporting code.
4. It owns no forms or validation. Every report mapping has no form bean and `validate="false"`, so
   there is no Struts form/Validator state to port — unlike Intake (`intakeForm` + hand-rolled
   validation) or Workbench (two `DynaValidatorForm`s).
5. The defects it carries are local and cheap to fix on the way out: the concatenated `as-of` date in
   `agedClaims` and the concatenated `order by` in `claimCounts`, plus `AdjusterWorkloadDetailAction`
   and `ReconciliationAction` duplicating `openClaimsByAdjuster` / `lossRatioByLine` results.

Second choice would be **Admin/reference** (tiny, ADJUSTER only), but it is worth less: it has a
missing `adjusterForm` bean and a missing `/admin/editAdjuster` mapping to fix first, and it removes
almost no complexity from the core. The Policy module is the natural third — read-only in practice,
but Settlement depends on `PolicyDAO.findById`, so it needs a published interface before it moves.
