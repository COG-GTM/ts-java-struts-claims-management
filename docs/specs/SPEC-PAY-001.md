# SPEC-PAY-001 — Payment issue, history, detail and remittance

Version: 0.2

Scope: the payment issue, history, detail and remittance request flows of the
claims application. Every rule below is taken either from a recorded transcript
(`Observed:`) or from a source file (`Read:`). Rule ids are permanent: if a rule
changes, a new version of that id is added and the earlier text is kept.

## Inputs

**PAY-R01 (v1)** — A payment is issued by POSTing `claimId`, `payeeName`,
`amount` and `paymentMethod` to `/claims/payment/issue.do`; the response status
is 200 and the request is forwarded to `/WEB-INF/jsp/payment/issue.jsp`.
Observed: payment_issue

**PAY-R02 (v1)** — `claimId` is parsed as an integer; when it is missing or not
parseable the value 119 is used. The same fallback applies to the history and
remittance flows.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22;
src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20;
src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:17;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**PAY-R03 (v1)** — The settlement for the claim is the SETTLEMENT row with the
highest `settlement_id` for that `claim_id`
(`select * from SETTLEMENT where claim_id = ? order by settlement_id desc`,
first row). It supplies the payment's `settlement_id` and the default amount.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,28;
src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-114

**PAY-R04 (v1)** — When the claim has no SETTLEMENT row the issue action
dereferences a null settlement, the exception reaches the global
`java.lang.Exception` mapping and the response is `/WEB-INF/jsp/error.jsp`
with status 200, no business fields and no validation errors. No PAYMENT row
is written.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,28;
src/main/java/com/northstar/claims/dao/SettlementDAO.java:106-110;
src/main/webapp/WEB-INF/struts-config.xml:59-60.
Confirmed against the running legacy app (`claimId=9999`, 2026-10-15).

**PAY-R05 (v1, superseded by v2)** — `amount` is parsed as a double; when it is missing or not
parseable the settlement amount of PAY-R03 is used. No other normalisation
(trimming, currency symbols, rounding) is applied before storage.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45

**PAY-R05 (v2)** — CHG-002, payment service only; the legacy app keeps v1 of
the rule. `amount` is parsed and falls back to the settlement amount exactly as
in v1. The parsed value, unrounded, is then compared with the
`settlement_amount` of the PAY-R03 settlement (the claim's latest saved
settlement). When it is greater, the response is status 200, screen `issue`,
no business fields and exactly one validation error,
`payment.amount.exceedsSettlement`; no PAYMENT row is written and the next
`payment_id` is not consumed. An amount equal to or below the settlement amount
is issued as in v1, so the omitted-amount fallback is never rejected. The check
runs after the settlement lookup: a claim with no settlement still fails as
PAY-R04 says. `NaN` is not greater than any amount and is issued as in v1.
Read: docs/changes/CHG-002-payment-above-settlement.md;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,30-31.
Observed (legacy, the behaviour this version replaces): manual run against
localhost Jetty from a fresh `make seed`, after replaying transcript
`settlement_save` (settlement 121, claim 119, `settlement_amount` 1000.0):
`POST /claims/payment/issue.do` `claimId=119`, `payeeName=Reserved Claimant`,
`amount=1500.00`, `paymentMethod=CHECK` answered 200,
`forward:/WEB-INF/jsp/payment/issue.jsp`, `paymentAmount` 1500.00,
`checkNumber` CHK-61, `paymentStatus` ISSUED, and wrote PAYMENT 61 (claim 119,
settlement 121, amount 1500.0) (2026-10-16).

**PAY-R06 (v1)** — `payeeName` and `paymentMethod` are stored exactly as sent:
no trimming, no case folding, and the `normalizeMethod` helper of
ClaimsActionSupport is not called, so `wire` is stored as `wire`, not `WIRE`.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:29,32;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:253-258

**PAY-R07 (v1)** — `payeeName` and `paymentMethod` are `NOT NULL` columns. When
either parameter is absent the insert fails, the failure reaches the global
exception mapping and the response is `/WEB-INF/jsp/error.jsp` with status
200. No PAYMENT row is written and the next `payment_id` is not consumed.
Read: src/main/resources/db/schema.sql:110,112;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:140-164;
src/main/webapp/WEB-INF/struts-config.xml:59-60.
Confirmed against the running legacy app (2026-10-15).

**PAY-R08 (v1)** — The payment form bean (`paymentForm`: `payeeName`, `amount`,
`paymentMethod`, `checkNumber`, all strings) is attached only to
`/payment/issue`; all four payment mappings are `validate="false"` and the
form's `validate` delegates to the empty base implementation, so no validation
errors are ever produced on these paths. `checkNumber` sent by the client is
ignored.
Read: src/main/webapp/WEB-INF/struts-config.xml:29-32,223-231,232-239,340-347,348-356;
src/main/java/com/northstar/claims/web/form/PaymentForm.java:13-16,64-70,73-75

## Issue

**PAY-R09 (v1)** — The new row gets `payment_id` = `max(payment_id) + 1` over
the whole PAYMENT table (`select coalesce(max(payment_id),0)+1 from PAYMENT`),
read in a separate connection before the insert. With the seed's 60 payments
the first issued payment is 61.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:25;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75;
src/main/resources/db/seed.sql:615-674.
Observed: payment_issue

**PAY-R10 (v1)** — `check_number` is the literal `CHK-` followed by the
unpadded payment id (`CHK-61`), unlike the zero-padded seed rows (`CHK-00001`).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33;
src/main/resources/db/seed.sql:615.
Observed: payment_issue

**PAY-R11 (v1)** — `issued_date` is the literal `2019-04-03` regardless of the
clock, and `status` is the literal `ISSUED`.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:34-35.
Observed: payment_issue

**PAY-R12 (v1)** — The insert writes all nine PAYMENT columns — `payment_id`,
`claim_id`, `settlement_id`, `payee_name`, `amount`, `payment_method`,
`check_number`, `issued_date`, `status` — in one statement; `issued_date` is
bound as a `java.sql.Date` parsed from the string.
Read: src/main/java/com/northstar/claims/dao/PaymentDAO.java:140-164;
src/main/resources/db/schema.sql:106-119

**PAY-R13 (v1)** — Issue writes to PAYMENT only. CLAIM and SETTLEMENT are read
and never updated: the claim's status stays `DENIED` for claim 119 and the
settlement row is unchanged.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:20-43;
src/main/resources/db/seed.sql:253,613.
Observed: payment_issue

**PAY-R14 (v1)** — The issue screen shows `paymentAmount` (money), `checkNumber`
and `paymentStatus`, from the `payment` request attribute. After issuing
1000.00 for claim 119 the fields are `1000.00`, `CHK-61`, `ISSUED`.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:37-41;
src/main/webapp/WEB-INF/jsp/payment/issue.jsp:19,21,23.
Observed: payment_issue

**PAY-R15 (v1)** — After the issue, the database holds exactly one PAYMENT row
for claim 119 (`payment.count.claim.119` = 1) with `amount` 1000.00 and
`status` `ISSUED` under id 61.
Observed: payment_issue

## History

**PAY-R16 (v1)** — `/claims/payment/history.do` accepts GET with no parameters;
`claimId` defaults per PAY-R02 and the response is 200 forwarded to
`/WEB-INF/jsp/payment/history.jsp`. It writes nothing.
Read: src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20-26.
Observed: payment_history

**PAY-R17 (v1)** — History lists the PAYMENT rows of the claim ordered by
`payment_id` ascending (`select * from PAYMENT where claim_id = ? order by
payment_id`).
Read: src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:21;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:100-119

**PAY-R18 (v1)** — Each listed payment contributes three fields whose names are
suffixed with the payment id: `checkNumber_<id>`, `paymentAmount_<id>` (money)
and `paymentStatus_<id>`. A claim with no payments produces no fields.
Read: src/main/webapp/WEB-INF/jsp/payment/history.jsp:26-32.
Observed: payment_history

**PAY-R19 (v1)** — After payment_issue, history for claim 119 shows exactly
`checkNumber_61` = `CHK-61`, `paymentAmount_61` = `1000.00`,
`paymentStatus_61` = `ISSUED`.
Observed: payment_history

## Detail

**PAY-R20 (v1)** — `/claims/payment/detail.do` takes `paymentId`; when it is
missing or not parseable the value 61 is used (the first payment the issue flow
creates). The response is 200 forwarded to `/WEB-INF/jsp/payment/detail.jsp`,
and nothing is written.
Read: src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**PAY-R21 (v1)** — Detail reads one row by primary key
(`select * from PAYMENT where payment_id = ?`) and exposes it as the `payment`
request attribute.
Read: src/main/java/com/northstar/claims/web/PaymentDetailAction.java:18-19;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:26-44

**PAY-R22 (v1)** — When no row has that id the DAO returns null, the JSP's
`bean:write` on the missing bean throws, and the response is
`/WEB-INF/jsp/error.jsp` with status 200 and no business fields.
Read: src/main/java/com/northstar/claims/dao/PaymentDAO.java:34-37;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14;
src/main/webapp/WEB-INF/struts-config.xml:59-60.
Confirmed against the running legacy app (`paymentId=9999`, 2026-10-15).

**PAY-R23 (v1)** — The detail screen shows `detailPaymentId` (integer),
`detailPaymentClaim` (integer), `detailPaymentAmount` (money),
`detailPaymentCheck`, `detailPaymentDate` (date) and `detailPaymentStatus`.
Read: src/main/webapp/WEB-INF/jsp/payment/detail.jsp:10-24

## Remittance

**PAY-R24 (v1)** — `/claims/payment/remittance.do` takes `claimId` (PAY-R02),
lists the claim's payments in the order of PAY-R17, and answers 200 forwarded
to `/WEB-INF/jsp/payment/remittance.jsp`. It writes nothing.
Read: src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:17-24

**PAY-R25 (v1)** — Each payment contributes `remitCheck_<id>`,
`remitDate_<id>` (date), `remitAmount_<id>` (money) and `remitStatus_<id>`.
Read: src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:16-24

**PAY-R26 (v1)** — The remittance screen also shows `remitClaimId` (integer),
`remitCount` (integer; the size of the list) and `remitTotal` (money; the
database `coalesce(sum(amount),0)` over the claim's payments). A claim with no
payments shows count 0 and total 0.00.
Read: src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:19-22;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:122-137;
src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:28-32

## Display formatting

**PAY-R27 (v1)** — `type="money"` formats the value with
`String.format("%.2f", Double.parseDouble(value))`, so `1000.0` prints as
`1000.00`; a value that cannot be parsed is printed unchanged.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-59.
Observed: payment_issue

**PAY-R28 (v1)** — `type="integer"` prints the integer value of the string;
`type="date"` prints the stored date as `yyyy-MM-dd` (the DAO already reads
`issued_date` as a `yyyy-MM-dd` string, so `2019-04-03` is shown as
`2019-04-03`). A null value prints as an empty string.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java:60-75,79-85;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:167-179

## Session and access

**PAY-R29 (v1)** — All four payment paths require an authenticated session;
the login redirect is the session filter's behaviour and is not part of the
payment slice.
Read: src/main/java/com/northstar/claims/web/AuthFilter.java:31-42

## Rule history

| Rule | Version | Change |
| --- | --- | --- |
| PAY-R01 | v1 | Initial rule, version 0.1 |
| PAY-R02 | v1 | Initial rule, version 0.1 |
| PAY-R03 | v1 | Initial rule, version 0.1 |
| PAY-R04 | v1 | Initial rule, version 0.1 |
| PAY-R05 | v1 | Initial rule, version 0.1 |
| PAY-R05 | v2 | CHG-002: an amount above the latest settlement is a validation error in the payment service, version 0.2 |
| PAY-R06 | v1 | Initial rule, version 0.1 |
| PAY-R07 | v1 | Initial rule, version 0.1 |
| PAY-R08 | v1 | Initial rule, version 0.1 |
| PAY-R09 | v1 | Initial rule, version 0.1 |
| PAY-R10 | v1 | Initial rule, version 0.1 |
| PAY-R11 | v1 | Initial rule, version 0.1 |
| PAY-R12 | v1 | Initial rule, version 0.1 |
| PAY-R13 | v1 | Initial rule, version 0.1 |
| PAY-R14 | v1 | Initial rule, version 0.1 |
| PAY-R15 | v1 | Initial rule, version 0.1 |
| PAY-R16 | v1 | Initial rule, version 0.1 |
| PAY-R17 | v1 | Initial rule, version 0.1 |
| PAY-R18 | v1 | Initial rule, version 0.1 |
| PAY-R19 | v1 | Initial rule, version 0.1 |
| PAY-R20 | v1 | Initial rule, version 0.1 |
| PAY-R21 | v1 | Initial rule, version 0.1 |
| PAY-R22 | v1 | Initial rule, version 0.1 |
| PAY-R23 | v1 | Initial rule, version 0.1 |
| PAY-R24 | v1 | Initial rule, version 0.1 |
| PAY-R25 | v1 | Initial rule, version 0.1 |
| PAY-R26 | v1 | Initial rule, version 0.1 |
| PAY-R27 | v1 | Initial rule, version 0.1 |
| PAY-R28 | v1 | Initial rule, version 0.1 |
| PAY-R29 | v1 | Initial rule, version 0.1 |

