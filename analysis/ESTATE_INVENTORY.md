# Struts estate inventory — NorthStar claims

Built by reading `src/main/webapp/WEB-INF/struts-config*.xml`, `web.xml`, the JSPs under
`src/main/webapp/WEB-INF/jsp`, and `com.northstar.claims.dao` (plus the action classes needed to
attribute SQL to screens). `docs/ARCHITECTURE.txt` was not used.

Module wiring from `web.xml`: default module = `struts-config.xml` + `struts-config-claims.xml`
(the latter declares zero actions and zero form beans — it exists only as a second descriptor for
the default module). Prefix module `/admin` = `struts-config-admin.xml`. `ActionServlet` is mapped
to `*.do`; `AuthFilter` also maps to `*.do` and redirects to `/login.do` unless the session has a
`user` attribute.

## 1. Action mappings

### 1a. Default module (`struts-config.xml`)

| Path | Action class (`com.northstar.claims.web.*`) | Form bean | scope | validate | input | Forwards (name → path) |
|---|---|---|---|---|---|---|
| `/login` | `LoginAction` | `loginForm` | request | false | — | `login` → `/WEB-INF/jsp/login.jsp`; `home` → `/WEB-INF/jsp/home.jsp` |
| `/home` | `HomeAction` | — | — | false | — | `home` → `/WEB-INF/jsp/home.jsp` |
| `/logout` | `LogoutAction` | — | — | false | — | none (returns global `login`) |
| `/policy/search` | `PolicySearchAction` | `policySearchForm` | request | false | — | `policySearch` → `/WEB-INF/jsp/policy/search.jsp` |
| `/policy/view` | `PolicyViewAction` | `policyViewForm` | request | false | — | `policyView` → `/WEB-INF/jsp/policy/view.jsp` |
| `/policy/list` | `PolicyListAction` | — | — | false | — | `policyList` → `/WEB-INF/jsp/policy/list.jsp` |
| `/policy/coverages` | `PolicyCoveragesAction` | — | — | false | — | `coverages` → `/WEB-INF/jsp/policy/coverages.jsp` |
| `/policy/coverageDetail` | `PolicyCoverageDetailAction` | — | — | false | — | `coverageDetail` → `/WEB-INF/jsp/policy/coverageDetail.jsp` |
| `/policy/insuredParty` | `PolicyInsuredPartyAction` | — | — | false | — | `insuredParty` → `/WEB-INF/jsp/policy/insuredParty.jsp` |
| `/policy/renewal` | `PolicyRenewalAction` | — | — | false | — | `renewal` → `/WEB-INF/jsp/policy/renewal.jsp` |
| `/intake/new` | `IntakeNewAction` | `intakeForm` | request | false | — | `intake` → `/WEB-INF/jsp/intake/new.jsp` |
| `/intake/submit` | `IntakeSubmitAction` | `intakeForm` | request | false | `/WEB-INF/jsp/intake/new.jsp` | `input` → `/WEB-INF/jsp/intake/new.jsp`; `confirm` → `/WEB-INF/jsp/intake/confirm.jsp` |
| `/intake/confirm` | `IntakeConfirmAction` | — | — | false | — | `confirm` → `/WEB-INF/jsp/intake/confirm.jsp` |
| `/workbench/list` | `WorkbenchListAction` | `workbenchForm` | request | false | — | `workbenchList` → `/WEB-INF/jsp/workbench/list.jsp` |
| `/workbench/view` | `WorkbenchViewAction` | `workbenchForm` | request | false | — | `workbenchView` → `/WEB-INF/jsp/workbench/view.jsp` |
| `/workbench/assign` | `WorkbenchAssignAction` | — | — | false | — | `workbenchView` → `/WEB-INF/jsp/workbench/view.jsp` |
| `/workbench/status` | `WorkbenchStatusAction` | `statusDyn` (`DynaValidatorForm`) | request | false | — | `workbenchView` → `/WEB-INF/jsp/workbench/view.jsp` |
| `/workbench/reserve` | `WorkbenchReserveAction` | `amountDyn` (`DynaValidatorForm`) | request | false | — | `workbenchView` → `/WEB-INF/jsp/workbench/view.jsp` |
| `/workbench/note` | `WorkbenchNoteAction` | — | — | false | — | `workbenchView` → `/WEB-INF/jsp/workbench/view.jsp` |
| `/workbench/notes` | `WorkbenchNoteHistoryAction` | — | — | false | — | `notes` → `/WEB-INF/jsp/workbench/notes.jsp` |
| `/workbench/reserveHistory` | `WorkbenchReserveHistoryAction` | — | — | false | — | `reserveHistory` → `/WEB-INF/jsp/workbench/reserveHistory.jsp` |
| `/workbench/statusHistory` | `WorkbenchStatusHistoryAction` | — | — | false | — | `statusHistory` → `/WEB-INF/jsp/workbench/statusHistory.jsp` |
| `/settlement/calculate` | `SettlementCalculateAction` | `settlementForm` | request | false | — | `settlement` → `/WEB-INF/jsp/settlement/calculate.jsp` |
| `/settlement/save` | `SettlementSaveAction` | `settlementForm` | request | false | — | `settlement` → `/WEB-INF/jsp/settlement/save.jsp` |
| `/settlement/detail` | `SettlementDetailAction` | — | — | false | — | `settlementDetail` → `/WEB-INF/jsp/settlement/detail.jsp` |
| `/payment/issue` | `PaymentIssueAction` | `paymentForm` | request | false | — | `payment` → `/WEB-INF/jsp/payment/issue.jsp` |
| `/payment/history` | `PaymentHistoryAction` | — | — | false | — | `payment` → `/WEB-INF/jsp/payment/history.jsp` |
| `/payment/detail` | `PaymentDetailAction` | — | — | false | — | `paymentDetail` → `/WEB-INF/jsp/payment/detail.jsp` |
| `/payment/remittance` | `PaymentRemittanceAction` | — | — | false | — | `remittance` → `/WEB-INF/jsp/payment/remittance.jsp` |
| `/report/index` | `ReportIndexAction` | — | — | false | — | `reportIndex` → `/WEB-INF/jsp/report/index.jsp` |
| `/report/openByAdjuster` | `OpenReportAction` | — | — | false | — | `report` → `/WEB-INF/jsp/report/openByAdjuster.jsp` |
| `/report/lossRatio` | `LossRatioReportAction` | — | — | false | — | `report` → `/WEB-INF/jsp/report/lossRatio.jsp` |
| `/report/agedClaims` | `AgedClaimsReportAction` | — | — | false | — | `report` → `/WEB-INF/jsp/report/agedClaims.jsp` |
| `/report/adjusterWorkload` | `AdjusterWorkloadDetailAction` | — | — | false | — | `adjusterWorkload` → `/WEB-INF/jsp/report/adjusterWorkload.jsp` |
| `/report/claimAgingDetail` | `ClaimAgingDetailAction` | — | — | false | — | `claimAgingDetail` → `/WEB-INF/jsp/report/claimAgingDetail.jsp` |
| `/report/reconciliation` | `ReconciliationAction` | — | — | false | — | `reconciliation` → `/WEB-INF/jsp/report/reconciliation.jsp` |
| `/report/premiumDetail` | `PremiumDetailAction` | — | — | false | — | `premiumDetail` → `/WEB-INF/jsp/report/premiumDetail.jsp` |

Global forwards (available to every mapping above): `login` → `login.jsp`, `home` → `home.jsp`,
`intakeInput` → `intake/new.jsp` (never referenced by any action), `error` → `error.jsp`.
Global exception: `java.lang.Exception` → `error.jsp` (key `errors.system`).

### 1b. `/admin` module (`struts-config-admin.xml`)

| Path (effective URL) | Action class | Form bean | scope | validate | Forwards |
|---|---|---|---|---|---|
| `/adjusters` (`/admin/adjusters.do`) | `com.northstar.claims.web.admin.AdjusterAdminAction` | `adjusterForm` | request | false | `adjusters` → `/WEB-INF/jsp/admin/adjusters.jsp`; `editAdjuster` → `/WEB-INF/jsp/admin/editAdjuster.jsp` |
| `/reference` (`/admin/reference.do`) | `com.northstar.claims.web.admin.ReferenceDataAction` | — | — | false | `reference` → `/WEB-INF/jsp/admin/reference.jsp` |

`adjusterForm` is named on the mapping but no `<form-bean>` with that name is declared in the admin
descriptor (or any descriptor); the class `web.form.AdjusterForm` exists but is unwired. The admin
module also declares no `<global-forwards>` and no `<plug-in>`, so the validator is not installed
there.

### 1c. `struts-config-claims.xml`

Empty `<action-mappings/>`, no form beans, no forwards. Loaded into the default module alongside
`struts-config.xml` purely as a placeholder ("retained for flat claim URLs").

### 1d. Form beans and validation

| Form bean | Type | Used by | Validation rules in `validation.xml` |
|---|---|---|---|
| `loginForm` | `web.form.LoginForm` | `/login` | none |
| `policySearchForm` | `web.form.PolicySearchForm` | `/policy/search` | none |
| `policyViewForm` | `web.form.PolicyViewForm` | `/policy/view` | none |
| `intakeForm` | `web.form.IntakeForm` | `/intake/new`, `/intake/submit` | `claimantName`, `lossDate`, `description` required |
| `workbenchForm` | `web.form.WorkbenchForm` | `/workbench/list`, `/workbench/view` | none |
| `settlementForm` | `web.form.SettlementForm` | `/settlement/calculate`, `/settlement/save` | none |
| `paymentForm` | `web.form.PaymentForm` | `/payment/issue` | none |
| `deadForm` | `web.form.DeadForm` | no mapping | none |
| `searchDyn` | `DynaValidatorForm` (`lineOfBusiness`) | no mapping | `lineOfBusiness` required |
| `statusDyn` | `DynaValidatorForm` (`status`) | `/workbench/status` | `status` required |
| `amountDyn` | `DynaValidatorForm` (`amount`) | `/workbench/reserve` | `amount` required |

Every mapping in every descriptor is `validate="false"`, so none of the `validation.xml` rules ever
execute. Actions also never read the form bean: they pull `request.getParameter(...)` directly
(e.g. `IntakeSubmitAction` re-implements the three `intakeForm` required-checks by hand and forwards
to a hardcoded `new ActionForward("/WEB-INF/jsp/intake/new.jsp", false)` instead of the mapping's
`input`). Unwired form classes: `AdjusterForm`, `ReferenceForm`, `BaseForm`, `DeadForm`.

## 2. JSPs

`nav.jsp` and `footer.jsp` are static includes in every full-page JSP except `login.jsp` and
`error.jsp`, which include no fragment at all. `header.jsp` is included by only 15 pages
(`home.jsp`, `policy/coverageDetail|insuredParty|renewal`, `workbench/assignment|notes|
reserveHistory|statusHistory`, `settlement/detail`, `payment/detail|remittance`,
`report/adjusterWorkload|claimAgingDetail|premiumDetail|reconciliation`); the remaining pages
inline an equivalent banner + navigation row of their own. Either way the same global links
(`/logout.do`, `/policy/list.do`, `/workbench/list.do`, `/report/openByAdjuster.do`,
`/policy/search.do`, `/intake/new.do`, `/payment/history.do`) appear on every authenticated screen,
which is why each screen depends on the workbench, policy, report and payment modules.

| JSP | Reached as forward from | Submits to (form) | Outbound links (excluding header/nav/footer) |
|---|---|---|---|
| `login.jsp` | `/login` (`login`), global `login` | `<html:form action="/login.do">` → `/login` | — |
| `home.jsp` | `/home`, `/login` (`home`), global `home` | — | `policy/list.do`, `workbench/list.do`, `report/index.do` |
| `error.jsp` | global exception / global `error` | — | `/login.do` |
| `header.jsp` (15 pages), `nav.jsp`, `footer.jsp` | include-only | — | global nav links (above) |
| `intake/new.jsp` | `/intake/new`, `/intake/submit` (`input`), global `intakeInput` | `<form action="intake/submit.do" method="post">` → `/intake/submit` | — |
| `intake/confirm.jsp` | `/intake/submit` (`confirm`), `/intake/confirm` | — | `../workbench/view.do?claimId=…` |
| `policy/search.jsp` | `/policy/search` | `<html:form action="/policy/search.do">` → `/policy/search` | — |
| `policy/list.jsp` | `/policy/list` | — | `view.do?policyId=…` |
| `policy/view.jsp` | `/policy/view` | — | `coverages.do`, `coverageDetail.do`, `insuredParty.do`, `renewal.do` (all `?policyId=…`) |
| `policy/coverages.jsp` | `/policy/coverages` | — | — |
| `policy/coverageDetail.jsp` | `/policy/coverageDetail` | — | — |
| `policy/insuredParty.jsp` | `/policy/insuredParty` | — | — |
| `policy/renewal.jsp` | `/policy/renewal` | — | — |
| `workbench/list.jsp` | `/workbench/list` | — | `view.do?claimId=…` |
| `workbench/view.jsp` | `/workbench/view`, `/workbench/assign`, `/workbench/status`, `/workbench/reserve`, `/workbench/note` | — | `assign.do`, `note.do`, `reserve.do`, `status.do?status=INVESTIGATING`, `notes.do`, `reserveHistory.do`, `statusHistory.do` |
| `workbench/note.jsp` | **no forward** (orphan) | `<html:form action="/workbench/note.do">` → `/workbench/note` | — |
| `workbench/assignment.jsp` | **no forward** (orphan) | `<form method="post" action="assign.do">` → `/workbench/assign` | — |
| `workbench/notes.jsp` | `/workbench/notes` | — | — |
| `workbench/reserveHistory.jsp` | `/workbench/reserveHistory` | — | — |
| `workbench/statusHistory.jsp` | `/workbench/statusHistory` | — | — |
| `settlement/calculate.jsp` | `/settlement/calculate` | — | `save.do`, `detail.do?claimId=…` |
| `settlement/save.jsp` | `/settlement/save` | — | — |
| `settlement/detail.jsp` | `/settlement/detail` | — | — |
| `payment/issue.jsp` | `/payment/issue` | — | — |
| `payment/history.jsp` | `/payment/history` | — | `detail.do?paymentId=61` (hardcoded id), `remittance.do?claimId=…` |
| `payment/detail.jsp` | `/payment/detail` | — | — |
| `payment/remittance.jsp` | `/payment/remittance` | — | — |
| `report/index.jsp` | `/report/index` | — | `openByAdjuster.do`, `lossRatio.do`, `agedClaims.do`, `adjusterWorkload.do`, `claimAgingDetail.do`, `reconciliation.do`, `premiumDetail.do` |
| `report/openByAdjuster.jsp` | `/report/openByAdjuster` | — | — |
| `report/lossRatio.jsp` | `/report/lossRatio` | — | — |
| `report/agedClaims.jsp` | `/report/agedClaims` | — | — |
| `report/adjusterWorkload.jsp` | `/report/adjusterWorkload` | — | — |
| `report/claimAgingDetail.jsp` | `/report/claimAgingDetail` | — | — |
| `report/reconciliation.jsp` | `/report/reconciliation` | — | — |
| `report/premiumDetail.jsp` | `/report/premiumDetail` | — | — |
| `admin/adjusters.jsp` | `/admin/adjusters` (`adjusters`) | — | `editAdjuster.do` (no such mapping in the admin module) |
| `admin/editAdjuster.jsp` | `/admin/adjusters` (`editAdjuster`) | `<html:form action="/admin/adjusters.do">` | — |
| `admin/reference.jsp` | `/admin/reference` | — | — |

Link hazards visible in the markup: `intake/new.jsp` posts to the relative path `intake/submit.do`
while the page is served under `/intake/new.do`, so the browser resolves `/intake/intake/submit.do`;
`admin/editAdjuster.jsp` posts to `/admin/adjusters.do` through `<html:form>`, which prepends the
module prefix again; `admin/adjusters.jsp` links to `editAdjuster.do`, which has no mapping. No JSP
anywhere links into the `/admin` module, and no JSP links to `payment/issue.do` — both are reachable
only by typed URL or by the capture harness.

## 3. SQL in `com.northstar.claims.dao`

Every statement runs through `ConnectionPool` (JNDI `jdbc/ClaimsDB`).

| DAO / method | Statement | Tables | Kind | Called from |
|---|---|---|---|---|
| `ClaimDAO.findById` | `select * from CLAIM where claim_id = ?` | CLAIM | prepared | `ClaimsActionSupport.findClaim` (workbench/note/reserve/status screens), `WorkbenchNoteHistoryAction`, `WorkbenchReserveHistoryAction`, `WorkbenchStatusHistoryAction`, `ClaimManager` |
| `ClaimDAO.findAll` | `select * from CLAIM order by claim_id` | CLAIM | statement | unused |
| `ClaimDAO.count` | `select count(*) from CLAIM` | CLAIM | statement | unused |
| `ClaimDAO.delete` | `delete from CLAIM where claim_id = ?` | CLAIM | prepared | unused |
| `ClaimDAO.findByStatus` | `select * from CLAIM where status = ? order by claim_id` | CLAIM | prepared | `WorkbenchListAction`, `ClaimAgingDetailAction`, `ClaimManager` |
| `ClaimDAO.search` | `select * from CLAIM where description like '%<term>%'` + optional `and status = '<status>'` | CLAIM | **concatenated** (injectable) | `ClaimManager.search` only (no action) |
| `ClaimDAO.assign` → `updateText` | `update CLAIM set <column> = ? where claim_id = ?` (column interpolated) | CLAIM | prepared + interpolated identifier | `ClaimManager` only |
| `ClaimDAO.updateStatus` → `updateText` | same as above | CLAIM | prepared + interpolated identifier | `ClaimManager` only |
| `ClaimDAO.updateReserve` | `update CLAIM set reserve_amount = ? where claim_id = ?` | CLAIM | prepared | `ClaimManager` only |
| `ClaimDAO.insert` | `insert into CLAIM (claim_id,claim_number,policy_id,claimant_name,status,reserve_amount) values (?,?,?,?,?,?)` | CLAIM | prepared | unused (FNOL writes its own insert) |
| `PolicyDAO.findById` | `select * from POLICY where policy_id = ?` | POLICY | prepared | `PolicyViewAction`, `PolicyCoveragesAction`, `PolicyCoverageDetailAction`, `PolicyInsuredPartyAction`, `PolicyRenewalAction`, `SettlementCalculateAction`, `SettlementSaveAction` |
| `PolicyDAO.findAll` | `select * from POLICY order by policy_id` | POLICY | statement | `PolicyListAction`, `PremiumDetailAction` |
| `PolicyDAO.count` | `select count(*) from POLICY` | POLICY | statement | unused |
| `PolicyDAO.delete` | `delete from POLICY where policy_id = ?` | POLICY | prepared | unused |
| `PolicyDAO.findByLine` | `select * from POLICY where line_of_business = '<line>' order by policy_number` | POLICY | **concatenated** (injectable) | `PolicySearchAction` |
| `PolicyDAO.search` | `select * from POLICY where policy_number like ? or insured_name like ?` | POLICY | prepared | `PolicyManager` only |
| `PolicyDAO.updateStatus` | `update POLICY set status = ? where policy_id = ?` | POLICY | prepared | `PolicyManager` only |
| `NoteDAO.findById` | `select * from CLAIM_NOTE where note_id = ?` | CLAIM_NOTE | prepared | unused |
| `NoteDAO.findAll` | `select * from CLAIM_NOTE order by note_id` | CLAIM_NOTE | statement | unused |
| `NoteDAO.count` | `select count(*) from CLAIM_NOTE` | CLAIM_NOTE | statement | unused |
| `NoteDAO.delete` | `delete from CLAIM_NOTE where note_id = ?` | CLAIM_NOTE | prepared | unused |
| `NoteDAO.findByClaim` | `select * from CLAIM_NOTE where claim_id = ? order by note_date` | CLAIM_NOTE | prepared | `WorkbenchNoteHistoryAction` |
| `NoteDAO.insert` | `insert into CLAIM_NOTE values (?,?,?,?,?)` (positional, no column list) | CLAIM_NOTE | prepared | unused — `WorkbenchNoteAction` never persists the note |
| `PaymentDAO.findById` | `select * from PAYMENT where payment_id = ?` | PAYMENT | prepared | `PaymentDetailAction` |
| `PaymentDAO.findAll` | `select * from PAYMENT order by payment_id` | PAYMENT | statement | unused |
| `PaymentDAO.count` | `select count(*) from PAYMENT` | PAYMENT | statement | unused |
| `PaymentDAO.delete` | `delete from PAYMENT where payment_id = ?` | PAYMENT | prepared | unused |
| `PaymentDAO.findByClaim` | `select * from PAYMENT where claim_id = ? order by payment_id` | PAYMENT | prepared | `PaymentHistoryAction`, `PaymentRemittanceAction` |
| `PaymentDAO.totalIssued` | `select coalesce(sum(amount),0) from PAYMENT where claim_id = ?` | PAYMENT | prepared | `PaymentRemittanceAction` |
| `PaymentDAO.insert` | `insert into PAYMENT (payment_id,claim_id,settlement_id,payee_name,amount,payment_method,check_number,issued_date,status) values (?,?,?,?,?,?,?,?,?)` | PAYMENT | prepared | `PaymentIssueAction` |
| `SettlementDAO.findById` | `select * from SETTLEMENT where settlement_id = ?` | SETTLEMENT | prepared | unused |
| `SettlementDAO.findAll` | `select * from SETTLEMENT order by settlement_id` | SETTLEMENT | statement | unused |
| `SettlementDAO.count` | `select count(*) from SETTLEMENT` | SETTLEMENT | statement | unused |
| `SettlementDAO.delete` | `delete from SETTLEMENT where settlement_id = ?` | SETTLEMENT | prepared | unused |
| `SettlementDAO.findByClaim` | `select * from SETTLEMENT where claim_id = ? order by settlement_id desc` | SETTLEMENT | prepared | `SettlementDetailAction`, `PaymentIssueAction` |
| `SettlementDAO.save` | `insert into SETTLEMENT (settlement_id,claim_id,covered_amount,deductible_applied,depreciation,capped_at_limit,settlement_amount,calculated_by,calculated_date) values (?,?,?,?,?,?,?,?,?)` | SETTLEMENT | prepared | `SettlementSaveAction` |
| `AdjusterDAO.findById` | `select * from ADJUSTER where adjuster_id = ?` | ADJUSTER | prepared | unused |
| `AdjusterDAO.findAll` | `select * from ADJUSTER order by adjuster_id` | ADJUSTER | statement | `AdjusterAdminAction` |
| `AdjusterDAO.count` | `select count(*) from ADJUSTER` | ADJUSTER | statement | unused |
| `AdjusterDAO.delete` | `delete from ADJUSTER where adjuster_id = ?` | ADJUSTER | prepared | unused |
| `AdjusterDAO.authenticate` | `select * from ADJUSTER where username = ? and password = ? and active = true` | ADJUSTER | prepared | unused — `LoginAction` hardcodes credentials |
| `AdjusterDAO.findActive` | `select * from ADJUSTER where active = true order by full_name` | ADJUSTER | statement | unused |
| `ReportDAO.openClaimsByAdjuster` | `select assigned_adjuster, count(*) as open_count, coalesce(sum(reserve_amount),0) as reserve_total from CLAIM where status in ('OPEN','INVESTIGATING') group by assigned_adjuster order by assigned_adjuster` | CLAIM | statement | `OpenReportAction`, `AdjusterWorkloadDetailAction` |
| `ReportDAO.lossRatioByLine` | `select p.line_of_business, sum(p.annual_premium), coalesce(sum(c.reserve_amount),0), coalesce(sum(pay.amount),0) from POLICY p left join CLAIM c on p.policy_id = c.policy_id left join PAYMENT pay on c.claim_id = pay.claim_id group by p.line_of_business order by p.line_of_business` | POLICY, CLAIM, PAYMENT | statement | `LossRatioReportAction`, `ReconciliationAction` |
| `ReportDAO.agedClaims` | `select case when datediff('day', reported_date, date '<report.asof.date>') … end as age_bucket, count(*), coalesce(sum(reserve_amount),0) from CLAIM where status not in ('CLOSED','DENIED') group by …` | CLAIM | **concatenated** (config value interpolated) | `AgedClaimsReportAction` |
| `ReportDAO.claimCounts` | `select status, count(*) as count from CLAIM group by status order by <orderBy>` | CLAIM | **concatenated** (order-by interpolated) | unused |

Tables in `src/main/resources/db/schema.sql`: `POLICY`, `INSURED_PARTY`, `COVERAGE`, `CLAIM`,
`CLAIM_NOTE`, `RESERVE_HISTORY`, `SETTLEMENT`, `PAYMENT`, `ADJUSTER`. `INSURED_PARTY`, `COVERAGE`
and `RESERVE_HISTORY` are never read by any DAO or action — the coverage, insured-party and
reserve-history screens render empty lists built in the action.

### SQL that is *not* in the DAO package (matters for any extraction)

| Location | Statement | Tables |
|---|---|---|
| `ClaimsActionSupport.nextId` | `select coalesce(max(<table>_id),0)+1 from <table>` | CLAIM (`IntakeSubmitAction`), PAYMENT (`PaymentIssueAction`) |
| `ClaimsActionSupport.countRows` | `select count(*) from <table>` | any |
| `ClaimsActionSupport.update/selectString/selectStrings/selectAmount` | arbitrary caller-supplied SQL | any |
| `IntakeSubmitAction` | `insert into CLAIM values (?,?,9001,?,?,?,?,?,'OPEN',0,'adjuster1','supervisor',?)` — positional, hardcoded policy id | CLAIM |
| `WorkbenchStatusAction` | `update CLAIM set status = '<status>' where claim_id = <id>` | CLAIM |
| `WorkbenchAssignAction` | `update CLAIM set assigned_adjuster = '<adjuster>' where claim_id = <id>` | CLAIM |
| `WorkbenchReserveAction` | `update CLAIM set reserve_amount = <amount> where claim_id = <id>` | CLAIM |

These open a second `DriverManager` connection to `jdbc:hsqldb:file:${claims.db.path}` and bypass
`ConnectionPool` entirely, so the write path for the workbench and FNOL does not go through the DAO
layer at all. The `service` package (`ClaimManager`, `PolicyManager`, `SettlementService`) is dead
except for `SettlementCalculator`, which is used by the two settlement actions and touches no SQL.

## 4. Candidate modules by actual coupling

| Module | Actions | JSPs | DAOs / SQL | Tables | Inbound coupling |
|---|---|---|---|---|---|
| **A. Payments** | `/payment/issue`, `/payment/history`, `/payment/detail`, `/payment/remittance` | `payment/issue.jsp`, `history.jsp`, `detail.jsp`, `remittance.jsp` | `PaymentDAO` (all methods), `SettlementDAO.findByClaim` (read) | PAYMENT (writes), SETTLEMENT (read) | entered from `nav.jsp` (`payment/history.do`) only; nothing else calls into it |
| **B. Reporting** | `/report/index`, `openByAdjuster`, `lossRatio`, `agedClaims`, `adjusterWorkload`, `claimAgingDetail`, `reconciliation`, `premiumDetail` | the eight `report/*.jsp` | `ReportDAO` (read-only), plus `PolicyDAO.findAll`, `ClaimDAO.findByStatus` | CLAIM, POLICY, PAYMENT (all read-only) | entered from `header/nav`, `home.jsp`; no writes, no one depends on it |
| **C. Policy** | `/policy/list`, `search`, `view`, `coverages`, `coverageDetail`, `insuredParty`, `renewal` | the seven `policy/*.jsp` | `PolicyDAO` | POLICY (read), COVERAGE + INSURED_PARTY (declared, unread) | settlement reads `PolicyDAO.findById` for limits/deductible; reporting reads `POLICY` |
| **D. Settlement** | `/settlement/calculate`, `save`, `detail` | `settlement/*.jsp` | `SettlementDAO`, `PolicyDAO.findById`, `SettlementCalculator` | SETTLEMENT (write), POLICY + CLAIM (read) | payments reads the settlement it produced; `ClaimsActionSupport.findClaim` |
| **E. Claim core (workbench + FNOL)** | `/workbench/*` (9 mappings), `/intake/new`, `submit`, `confirm`, `/home` | `workbench/*.jsp`, `intake/*.jsp`, `home.jsp` | `ClaimDAO`, `NoteDAO`, plus the raw SQL in `ClaimsActionSupport`, `IntakeSubmitAction`, `Workbench{Status,Assign,Reserve}Action` | CLAIM (write), CLAIM_NOTE, RESERVE_HISTORY (unread) | everything reaches it: `nav.jsp`, intake confirm, reporting aggregates, settlement/payment key off `claimId` |
| **F. Identity / admin** | `/login`, `/logout`, `/admin/adjusters`, `/admin/reference`, `AuthFilter` | `login.jsp`, `admin/*.jsp`, `error.jsp` | `AdjusterDAO.findAll` (`authenticate` unused) | ADJUSTER | `AuthFilter` gates every `*.do`; login is hardcoded, so the module is barely wired to its own table |

Shared spine that no module owns: `ClaimsActionSupport` (every action extends it and it carries both
the connection factory and ad-hoc SQL helpers), `header/nav/footer.jsp`, `ApplicationResources`,
`ConnectionPool` and the `ns` tag library.

### Cleanest first extraction: Payments (module A)

- Its DAO surface is one class. `PaymentDAO` is the only writer of `PAYMENT`, and `PAYMENT` is read
  by exactly one statement outside the module (`ReportDAO.lossRatioByLine`), which is an aggregate
  that could be served by a read replica or a view rather than in-process code.
- All four actions are plain read/insert over prepared statements, and the only shared-base SQL they
  use is `nextId("PAYMENT")` in `PaymentIssueAction`, a max-id lookup on PAYMENT alone, replaceable
  by an identity column. The rest of their use of `ClaimsActionSupport` is non-SQL parameter
  parsing — `integer(...)` in all four actions and `decimal(...)` in `PaymentIssueAction` — so the
  base class still has to be dropped or re-provided, but as two small helpers, not as a second
  connection factory or an ad-hoc SQL surface.
- Its only inbound edge from outside the module is one link, `nav.jsp` → `payment/history.do`
  (`/payment/issue.do` is not linked from any JSP at all); the other payment links live inside
  `payment/history.jsp`. No action outside the module forwards into a payment forward name, so the
  seam is a URL boundary rather than a call graph.
- Its only outbound dependency is `SettlementDAO.findByClaim(claimId)` in `PaymentIssueAction` — a
  single read that becomes one API call or one query against a settlement view.
- It carries no validator, no `DynaValidatorForm`, no module prefix and no `html:form`: the four JSPs
  are display-only, so the rewritten screens do not have to reproduce any Struts form binding.

Runner-up is Reporting (module B): larger, but read-only and with zero inbound dependencies, so it
can be lifted without a write-path cutover. It is second only because its queries span CLAIM, POLICY
and PAYMENT, so it needs data from three tables on day one.

Worst first candidate is Claim core (module E): it owns the write path, half of that write path is
string-concatenated SQL sitting in action classes rather than DAOs, and every other module keys off
`claimId`.
