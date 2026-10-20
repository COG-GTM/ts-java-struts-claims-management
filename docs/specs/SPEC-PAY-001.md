# SPEC-PAY-001 — Payment issue, history, detail and remittance

Version: 0.1

Scope: the four payment request flows of the claims application:
`/claims/payment/issue.do`, `history.do`, `detail.do` and `remittance.do`.
This is the current legacy behaviour, including behaviour that looks wrong.
Rule ids are permanent: if a rule changes, a new version of that id is added and
the earlier text is kept.

Every rule cites the source lines it comes from (`Read:`) and at least one
request against the running legacy app that shows it (`Observed:`).

* `Observed: payment_issue` is a transcript under `transcripts/`, written by
  `make capture`.
* `Observed: live:issue-post#1` is step 1 of case `issue-post` in
  `docs/specs/SPEC-PAY-001-proof.json`. Each case runs on a freshly seeded
  database plus its own precondition SQL, so no case depends on another.
  Re-run with `make payment-spec-proof` (rewrites the evidence) or
  `make payment-spec-check` (fails if legacy behaviour moved).
* `issue.jsp` is a confirmation page, not an input form. Every issue request is
  sent straight to the real `.do` endpoint by the proof tool, as a browser
  form or curl would send it.

Behaviour that could not be established is in
`docs/specs/OPEN-QUESTIONS.md` (OQ-02 to OQ-05), not here.
The interactive view of this spec is `docs/specs/SPEC-PAY-001.html`
(`make payment-spec-html`).

## Issue a payment

**PAY-R01 (v1)** — A payment is issued by sending `claimId`, `payeeName`,
`amount` and `paymentMethod` to `/claims/payment/issue.do`. The response is
200 and is forwarded to `/WEB-INF/jsp/payment/issue.jsp`, which shows three
fields: `paymentAmount`, `checkNumber` and `paymentStatus`. The payment id
and claim id are not shown.
Read: src/main/webapp/WEB-INF/struts-config.xml:223-231;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:37-43;
src/main/webapp/WEB-INF/jsp/payment/issue.jsp:16-25
Observed: payment_issue; live:issue-post#1

**PAY-R02 (v1)** — `issue.do` writes a payment on GET as well as on POST. The
query string is read the same way as a posted form.
Read: src/main/webapp/WEB-INF/struts-config.xml:223-231;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:19-36
Observed: live:issue-get#1

**PAY-R03 (v1)** — No payment request is validated by Struts: all four mappings
have `validate="false"`. `issue.do` populates `paymentForm` but the action
never reads it; it reads the raw request parameters.
Read: src/main/webapp/WEB-INF/struts-config.xml:223-238;
src/main/webapp/WEB-INF/struts-config.xml:340-355;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-32
Observed: live:issue-claim-fallback#1; live:issue-amount-raw#1

**PAY-R04 (v1)** — `claimId` is parsed with `Integer.parseInt`. When it is
missing or not an integer (`abc`, `1.5`) claim 119 is used.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37
Observed: live:issue-claim-fallback#1; live:issue-claim-fallback#2;
live:issue-claim-fallback#3

**PAY-R05 (v1)** — The claim's newest settlement is looked up: the SETTLEMENT
row with that `claim_id` and the highest `settlement_id`. Its id is stored as
the payment's `settlement_id`.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:28;
src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-115
Observed: live:issue-newest-settlement#1; live:issue-post#1

**PAY-R06 (v1)** — When the claim has no SETTLEMENT row, or the claim does not
exist, the request ends on the error page (status 200,
`/WEB-INF/jsp/error.jsp`) and no payment is written.
Read: src/main/java/com/northstar/claims/dao/SettlementDAO.java:109;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:28;
src/main/webapp/WEB-INF/struts-config.xml:59-60
Observed: live:issue-no-settlement#1; live:issue-no-settlement#2

**PAY-R07 (v1)** — `amount` is parsed with `Double.parseDouble`. When it is
missing or not parseable (`1,000.00`), the newest settlement's
`settlement_amount` is used. In the seed that amount is 0 for claims 1 and
119, so the payment is 0.00.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45;
src/main/resources/db/seed.sql:613
Observed: live:issue-amount-fallback-seed#1;
live:issue-amount-fallback-seed#2; live:issue-amount-fallback-seed#3;
live:issue-newest-settlement#1

**PAY-R08 (v1)** — Any value `Double.parseDouble` accepts is stored as sent,
with no rounding, sign or range check: `-50`, `1.005`, `1e3` and `NaN` are
all stored. `1.005` is stored as 1.005 and shown as 1.01.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:154
Observed: live:issue-amount-raw#1; live:issue-amount-raw#2;
live:issue-amount-raw#3; live:issue-amount-raw#5

**PAY-R09 (v1)** — The payment id is `coalesce(max(payment_id),0)+1` over the
whole PAYMENT table, read before the insert. Gaps are not filled: with payments
1–60 and 500 the next id is 501. On the seeded database the first new payment
is 61.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:25-26;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75
Observed: live:issue-post#1; live:issue-next-id#1

**PAY-R10 (v1)** — The check number is `"CHK-"` followed by the payment id
(`CHK-61`, `CHK-501`). A submitted `checkNumber` is ignored.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33
Observed: live:issue-check-number#1; live:issue-next-id#1

**PAY-R11 (v1)** — Every new payment has issued date `2019-04-03` and status
`ISSUED`, whatever the request contains.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:34-35
Observed: live:issue-post#1; live:issue-post#2

**PAY-R12 (v1)** — `payeeName` and `paymentMethod` are stored exactly as sent:
no trimming, no case change, no check against a list of methods (`wire` is
stored as `wire`).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:29;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:32;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:153-155
Observed: live:issue-raw-text#1

**PAY-R13 (v1)** — The only checks on `payeeName` and `paymentMethod` are the
PAYMENT column definitions. A missing `payeeName` or `paymentMethod`, a
`payeeName` over 100 characters or a `paymentMethod` over 20 characters makes
the insert fail: error page (status 200, `/WEB-INF/jsp/error.jsp`), no row
written. Exactly 100 and 20 characters are accepted.
Read: src/main/resources/db/schema.sql:110;
src/main/resources/db/schema.sql:112;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:140-159;
src/main/webapp/WEB-INF/struts-config.xml:59-60
Observed: live:issue-required-columns#1; live:issue-required-columns#2;
live:issue-column-limits#1; live:issue-column-limits#2;
live:issue-column-limits#3

**PAY-R14 (v1)** — A failed issue does not use up a payment id: after two
failed requests the next successful payment is still 61.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:25;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:67-69
Observed: live:issue-required-columns#3

**PAY-R15 (v1)** — Issue has no business checks. A payment is accepted for a
`DENIED` claim (119), a `CLOSED` claim (120), a claim that already has
payments, an amount far above the settlement amount, and the same request sent
twice.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-36;
src/main/resources/db/seed.sql:253-254
Observed: live:issue-post#1; live:issue-no-business-checks#1;
live:issue-no-business-checks#2; live:issue-no-business-checks#3

**PAY-R16 (v1)** — Any logged-in operator can issue a payment. An adjuster
login (`adjuster7`) issues payment 61 like the supervisor; the screen shows
the operator name.
Read: src/main/java/com/northstar/claims/web/LoginAction.java:21-24;
src/main/java/com/northstar/claims/web/AuthFilter.java:37-40;
src/main/webapp/WEB-INF/jsp/payment/issue.jsp:31-34
Observed: live:adjuster-issue#1

## Payment history

**PAY-R17 (v1)** — `history.do` lists the PAYMENT rows of one claim, ordered
by `payment_id` (not by insert order or date). `claimId` falls back to 119
when missing or not an integer; seeded claim 119 has no payments, so the list
is empty. Each row shows `checkNumber_<id>`, `paymentAmount_<id>` and
`paymentStatus_<id>`.
Read: src/main/webapp/WEB-INF/struts-config.xml:232-238;
src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20-26;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:107;
src/main/webapp/WEB-INF/jsp/payment/history.jsp:26-32
Observed: payment_history; live:history#1; live:history#2; live:history#3;
live:history-order#1

**PAY-R18 (v1)** — The history page shows the same two links twice. The
"Payment detail" link always opens `detail.do?paymentId=61`, whatever claim
is shown. The "Payment remittance" link opens `remittance.do?claimId=<claimId>`
for the claim shown (119 after a fallback).
Read: src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19
Observed: live:history#1; live:history#2; live:detail-default-61#2

## Payment detail

**PAY-R19 (v1)** — `detail.do` shows one payment: id, claim, payee, amount,
method, check number, issued date and status.
Read: src/main/webapp/WEB-INF/struts-config.xml:340-347;
src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:32;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:8-25
Observed: live:detail#1; live:issue-post#2

**PAY-R20 (v1)** — `paymentId` falls back to 61 when missing or not an
integer. When payment 61 exists it is shown; on the seeded database it does
not exist.
Read: src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37
Observed: live:detail-default-61#1; live:detail#2; live:detail#3

**PAY-R21 (v1)** — When the payment does not exist the detail page cannot be
drawn and the request ends on the error page (status 200,
`/WEB-INF/jsp/error.jsp`, "Reference status: null").
Read: src/main/java/com/northstar/claims/web/PaymentDetailAction.java:18-20;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14;
src/main/webapp/WEB-INF/struts-config.xml:59-60;
src/main/webapp/WEB-INF/jsp/error.jsp:9
Observed: live:detail#2; live:detail#3; live:detail#4

## Payment remittance

**PAY-R22 (v1)** — `remittance.do` lists the PAYMENT rows of one claim, ordered
by `payment_id`, with check, payee, issued date, amount and status, then the
claim id (`remitClaimId`), the row count (`remitCount`) and the total
(`remitTotal`). `claimId` falls back to 119 when missing or not an integer.
Read: src/main/webapp/WEB-INF/struts-config.xml:348-355;
src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:17-24;
src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:9-33
Observed: live:remittance#1; live:remittance#2; live:remittance#3;
live:history-order#2

**PAY-R23 (v1)** — The total is `coalesce(sum(amount),0)` over every PAYMENT
row of the claim, whatever its status: a `VOID` payment is added in, negative
amounts reduce it, and one `NaN` amount makes the total `NaN`. No payments
gives 0.00.
Read: src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:22;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:128
Observed: live:remittance-total#1; live:issue-amount-raw#4;
live:issue-amount-raw#6; live:remittance#2

## Display and access

**PAY-R24 (v1)** — Amounts are printed with `String.format("%.2f")`; `NaN`
prints as `NaN`. Issued dates are printed as `yyyy-MM-dd`.
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-59;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:67-75
Observed: live:issue-amount-raw#2; live:issue-amount-raw#6; live:detail#1

**PAY-R25 (v1)** — Payee and method are HTML-escaped on the detail and
remittance pages: a payee stored as `<b>A&B</b> Payee` is shown as that text,
not as markup.
Read: src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:18;
src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:19
Observed: live:issue-raw-text#2; live:issue-raw-text#3

**PAY-R26 (v1)** — Without a logged-in session every payment URL answers 302 to
`/claims/login.do` and nothing is written, including a POST or GET to
`issue.do`.
Read: src/main/java/com/northstar/claims/web/AuthFilter.java:31-43;
src/main/webapp/WEB-INF/web.xml:77-80
Observed: live:unauthenticated#1; live:unauthenticated#2;
live:unauthenticated#3; live:unauthenticated#4; live:unauthenticated#5

## Screen text checked against behaviour

| Claim | Where | Result | Evidence |
| --- | --- | --- | --- |
| "Payment history is displayed in issue order." | src/main/resources/ApplicationResources.properties:102 | Confirmed only when ids follow issue order: rows are ordered by `payment_id`, and payment 200 inserted after 300 is listed first | live:history-order#1 |
| "Operational record view" on the issue page | src/main/webapp/WEB-INF/jsp/payment/issue.jsp:29 | Contradicted: the page is shown after a payment row is written | live:issue-post#1 |
| "The check is ready for issue control review." | src/main/resources/ApplicationResources.properties:100 | Not verifiable: the slice has no review step and stores status `ISSUED` at once | live:issue-post#1 |
| Payment detail link on the history page | src/main/webapp/WEB-INF/jsp/payment/history.jsp:16 | Contradicted: it opens payment 61 for every claim | live:history#1 |

## Rule history

| Rule | Version | Change |
| --- | --- | --- |
| PAY-R01 | v1 | Initial rule, version 0.1 |
| PAY-R02 | v1 | Initial rule, version 0.1 |
| PAY-R03 | v1 | Initial rule, version 0.1 |
| PAY-R04 | v1 | Initial rule, version 0.1 |
| PAY-R05 | v1 | Initial rule, version 0.1 |
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
