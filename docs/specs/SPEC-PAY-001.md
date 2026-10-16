# SPEC-PAY-001 — Payment issue, history, detail and remittance

Version: 0.1

Scope: the payment issue, history, detail and remittance request flows of the
claims application (`/claims/payment/*.do`). Every rule below is taken either
from a recorded transcript (`Observed: <transcript>`), from a manual run against
the legacy app on localhost Jetty (`Observed: manual run ... step Lnn`, recorded
verbatim in `docs/specs/SPEC-PAY-001-live.json` by `tools/spec/pay_live_proof.py`,
with `db row nn` meaning the read-only JDBC probe in the same file), or from a
source file (`Read:`). Every rule has at least one `Observed:` citation; code facts
that could not be shown on the running app are in `docs/specs/OPEN-QUESTIONS.md`,
not here. Rule ids are permanent: if a rule changes, a new version of that id is
added and the earlier text is kept.

The live run started from a fresh `make seed` (payments 1-60, settlements 118,
119 and 120 for claims 118, 119 and 120 with settlement amounts 1118, 0 and 0),
signed in as `supervisor`. `docs/specs/SPEC-PAY-001.html` is the same content as
an interactive, self-contained evidence viewer.

## Access

**PAY-R01 (v1)** — Every payment URL requires a signed-in session. Without one,
`GET /claims/payment/history.do` and `POST /claims/payment/issue.do` answer 302
with `Location: /claims/login.do` and nothing is issued.
Observed: manual run against localhost Jetty, steps L01, L02;
Read: src/main/java/com/northstar/claims/web/AuthFilter.java:37-42;
src/main/webapp/WEB-INF/web.xml:73-80

## Issue

**PAY-R02 (v1)** — A payment is issued by POSTing `claimId`, `payeeName`,
`amount` and `paymentMethod` to `/claims/payment/issue.do`; the response status
is 200, the request is forwarded to `/WEB-INF/jsp/payment/issue.jsp`, and the
screen renders `paymentAmount`, `checkNumber` and `paymentStatus`.
Observed: payment_issue; manual run against localhost Jetty, step L14;
Read: src/main/webapp/WEB-INF/struts-config.xml:223-231;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:37-43;
src/main/webapp/WEB-INF/jsp/payment/issue.jsp:19-23

**PAY-R03 (v1)** — The issue mapping is not restricted to POST: the same
parameters sent by `GET /claims/payment/issue.do?...` issue a payment and
write a row exactly as a POST does.
Observed: manual run against localhost Jetty, step L26, db row 67;
Read: src/main/webapp/WEB-INF/struts-config.xml:223-231;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22

**PAY-R04 (v1)** — `claimId` is parsed as an integer; when it is missing or not
parseable the value 119 is used, and the payment is written against claim 119.
Observed: manual run against localhost Jetty, steps L16, L37, L38, db row 63;
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**PAY-R05 (v1)** — The payment is tied to the claim's newest settlement: the
`SETTLEMENT` row for the claim with the highest `settlement_id` supplies
`settlement_id` and the amount fallback. A claim with no settlement row cannot
be paid: the request ends on the system error page and no row is written.
Observed: manual run against localhost Jetty, steps L18, L42, L43, L44, db rows
61, 74 (after a second settlement 121 for claim 118, payment 74 carries
settlement_id 121 and amount 2750.00, not settlement 118's 1118);
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23-28;
src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-115

**PAY-R06 (v1)** — `amount` is parsed as a double; when it is missing or not
parseable (for example `abc` or `1,000`) the newest settlement's
`settlement_amount` is used, including 0 when that amount is 0.
Observed: manual run against localhost Jetty, steps L14, L15, L17, L28, L43;
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45

**PAY-R07 (v1)** — Anything `Double.parseDouble` accepts is a valid amount:
`-5` issues a payment of -5.00, `1e3` issues 1000.00, and ` 5 ` (surrounding
spaces) issues 5.00. No sign, range or format check is applied.
Observed: manual run against localhost Jetty, steps L25, L27, L29, db rows 66, 68, 70;
Read: src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31

**PAY-R08 (v1)** — `amount=NaN` is accepted: the payment is issued with
`paymentAmount` `NaN`, the stored `amount` is NaN (`0E0/0E0`), the detail
screen shows `NaN`, and every remittance total for that claim becomes `NaN`
from then on.
Observed: manual run against localhost Jetty, steps L32, L45, L47, db row 73;
Read: src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:122-137;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-58

**PAY-R09 (v1)** — The amount is stored as parsed, without rounding: `12.345`
is persisted as 12.345 in the `DOUBLE` column and only the screens round it to
`12.35`.
Observed: manual run against localhost Jetty, steps L16, L37, db row 63;
Read: src/main/java/com/northstar/claims/dao/PaymentDAO.java:150-158;
src/main/java/com/northstar/claims/model/Payment.java:18;
src/main/resources/db/schema.sql:111;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-58

**PAY-R10 (v1)** — `payeeName` and `paymentMethod` are stored exactly as sent:
case is kept (`wire`, `ach`), punctuation and HTML characters are kept
(`O'Brien <b>&</b> "Co"`), and an empty `payeeName=` is accepted and stored as
an empty string. The method is not normalised.
Observed: manual run against localhost Jetty, steps L14, L23, L31, L33, L34,
L46, db rows 61, 65, 72;
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:29,32;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:150-158

**PAY-R11 (v1)** — There is no Struts validation on the issue request: the
mapping has `validate="false"` and `PaymentForm.validate` returns an empty
`ActionErrors`. The form's `checkNumber` field is bound but never read: a
request carrying `checkNumber=MINE-1` still receives the generated `CHK-71`.
Observed: manual run against localhost Jetty, step L30, db row 71;
Read: src/main/webapp/WEB-INF/struts-config.xml:29-32,223-231;
src/main/java/com/northstar/claims/web/form/PaymentForm.java:13-16,73-75;
src/main/java/com/northstar/claims/web/form/BaseForm.java:31-33;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33

**PAY-R12 (v1)** — The only input checks are the database's: a request without
`payeeName` or without `paymentMethod`, a `payeeName` longer than 100 characters
or a `paymentMethod` longer than 20 characters ends on the system error page
(the `NOT NULL` / `VARCHAR` constraints reject the insert); exactly 100 and 20
characters are accepted.
Observed: manual run against localhost Jetty, steps L19, L20, L21, L22, L30, db row 71;
Read: src/main/resources/db/schema.sql:110,112;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:145-158;
src/main/webapp/WEB-INF/struts-config.xml:59-60

**PAY-R13 (v1)** — A failed issue writes nothing and consumes no identifier:
after five failed requests (L18-L22) the next successful issue is payment 65
and claim 118 still has three payments.
Observed: manual run against localhost Jetty, steps L18, L19, L20, L21, L22, L23, L24;
Read: src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:25-36

**PAY-R14 (v1)** — The new `payment_id` is `max(payment_id)+1` over the whole
`PAYMENT` table (61 on a fresh seed of 60 rows), and the check number is the
string `CHK-` followed by that id (`CHK-61`), with no zero padding.
Observed: payment_issue; manual run against localhost Jetty, step L14, db
rows 61-74 (count=74, max=74);
Read: src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:25,33;
src/main/resources/db/seed.sql:615-674

**PAY-R15 (v1)** — Every issued payment has `issued_date` `2019-04-03` and
`status` `ISSUED`, regardless of the request, the clock, or the `2019-04-01`
as-of date shown on the screens.
Observed: payment_issue; manual run against localhost Jetty, steps L33, L39, db rows 61-74;
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:34-35

**PAY-R16 (v1)** — A successful issue inserts exactly one `PAYMENT` row with
`payment_id`, `claim_id` (the resolved claim), `settlement_id` (the newest
settlement), `payee_name`, `amount`, `payment_method`, `check_number`,
`issued_date` and `status`.
Observed: payment_issue (`payment.count.claim.119` = 1); manual run against
localhost Jetty, db rows 61-74;
Read: src/main/java/com/northstar/claims/dao/PaymentDAO.java:140-164;
src/main/resources/db/schema.sql:106-119

**PAY-R17 (v1)** — The issue screen shows `paymentAmount` as money (`%.2f`),
`checkNumber` and `paymentStatus` as text, the message "The check is ready for
issue control review." and the screen-audit table (Screen context
"Operational record view", Operator = signed-in user, As of date `2019-04-01`).
The `paymentId` and `claimId` request attributes are not rendered.
Observed: payment_issue; manual run against localhost Jetty, step L14;
Read: src/main/webapp/WEB-INF/jsp/payment/issue.jsp:19-39;
src/main/resources/ApplicationResources.properties:100,242-245;
src/main/resources/northstar.properties:2

## History

**PAY-R18 (v1)** — `GET /claims/payment/history.do` forwards to
`/WEB-INF/jsp/payment/history.jsp` with status 200. `claimId` is parsed as an
integer and falls back to 119 when missing or not parseable.
Observed: payment_history; manual run against localhost Jetty, steps L04, L06, L36, L38;
Read: src/main/webapp/WEB-INF/struts-config.xml:232-238;
src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20-26;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**PAY-R19 (v1)** — The history lists every `PAYMENT` row for the claim in
ascending `payment_id` order, one row per payment with `checkNumber_<id>`,
`paymentAmount_<id>` (money) and `paymentStatus_<id>`; a claim with no payments
renders no rows and no other payment field.
Observed: payment_history; manual run against localhost Jetty, steps L04, L05, L36;
Read: src/main/java/com/northstar/claims/dao/PaymentDAO.java:100-119;
src/main/webapp/WEB-INF/jsp/payment/history.jsp:26-32

**PAY-R20 (v1)** — The history screen carries two identical link paragraphs.
The detail link is hard-coded to `detail.do?paymentId=61` whatever claim is
shown; the remittance link is `remittance.do?claimId=<resolved claimId>`.
Observed: manual run against localhost Jetty, steps L04 (claim 119), L36 (claim 118);
Read: src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19;
src/main/resources/ApplicationResources.properties:278-279

**PAY-R21 (v1)** — The history screen renders no count or total: the
`paymentCount` attribute the action sets is not used by the JSP, so the only
`ns:field` values on the page are the per-payment fields of PAY-R19.
Observed: manual run against localhost Jetty, steps L04, L36;
Read: src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:22-25;
src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-48

## Detail

**PAY-R22 (v1)** — `GET /claims/payment/detail.do` forwards to
`/WEB-INF/jsp/payment/detail.jsp` with status 200 for an existing payment.
`paymentId` is parsed as an integer and falls back to 61 when missing or not
parseable, so with no parameter the screen shows payment 61 once it exists.
Observed: manual run against localhost Jetty, steps L09, L33, L35;
Read: src/main/webapp/WEB-INF/struts-config.xml:340-347;
src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**PAY-R23 (v1)** — A `paymentId` with no `PAYMENT` row (9999, or the fallback
61 on a fresh seed, or `abc` which falls back to 61) ends on the system error
page: the JSP dereferences the null payment.
Observed: manual run against localhost Jetty, steps L07, L08, L10;
Read: src/main/java/com/northstar/claims/web/PaymentDetailAction.java:18-20;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:10;
src/main/webapp/WEB-INF/struts-config.xml:59-60

**PAY-R24 (v1)** — The detail screen renders `detailPaymentId` and
`detailPaymentClaim` as integers, `detailPaymentAmount` as money,
`detailPaymentCheck` and `detailPaymentStatus` as text, `detailPaymentDate` as
`yyyy-MM-dd`, and the payee and method as plain HTML-escaped cells without an
`ns:field` id (payee `O'Brien <b>&</b> "Co"` renders as
`O&#39;Brien &lt;b&gt;&amp;&lt;/b&gt; &quot;Co&quot;`; an empty payee renders
an empty cell), followed by the screen-audit table.
Observed: manual run against localhost Jetty, steps L09, L33, L34, L46;
Read: src/main/webapp/WEB-INF/jsp/payment/detail.jsp:10-39;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-77

## Remittance

**PAY-R25 (v1)** — `GET /claims/payment/remittance.do` forwards to
`/WEB-INF/jsp/payment/remittance.jsp` with status 200. `claimId` is parsed as
an integer and falls back to 119 when missing or not parseable.
Observed: manual run against localhost Jetty, steps L11, L12, L13;
Read: src/main/webapp/WEB-INF/struts-config.xml:348-355;
src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:17-24;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**PAY-R26 (v1)** — The remittance lists every payment of the claim in ascending
`payment_id` order with `remitCheck_<id>`, the payee as a plain cell,
`remitDate_<id>` (date), `remitAmount_<id>` (money) and `remitStatus_<id>`.
Observed: manual run against localhost Jetty, steps L12, L39;
Read: src/main/java/com/northstar/claims/dao/PaymentDAO.java:100-119;
src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:16-24

**PAY-R27 (v1)** — Below the rows the remittance shows `remitClaimId` (the
resolved claim), `remitCount` (number of rows) and `remitTotal`, which is
`coalesce(sum(amount),0)` over the claim's payments rendered as money: `0.00`
for a claim with no payments, `2237.00` for 1118 + 1118 + 1, and `NaN` once
any payment amount is NaN. Negative amounts reduce the total.
Observed: manual run against localhost Jetty, steps L11, L24, L40, L41, L47;
Read: src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:19-23;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:122-137;
src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:28-32

## Formatting and errors

**PAY-R28 (v1)** — Field formatting is the `ns:field` tag's: `money` is
`String.format("%.2f", Double.parseDouble(value))` (12.345 -> `12.35`,
-5 -> `-5.00`, 0 -> `0.00`, NaN -> `NaN`); `integer` is the parsed double cast
to long (`61`); `date` is re-parsed and re-formatted as `yyyy-MM-dd`
(`2019-03-15`, `2019-04-03`); every value is emitted as
`<span id="f_<name>">` with `&`, `<`, `>` and `"` escaped.
Observed: manual run against localhost Jetty, steps L09, L16, L17, L25, L32, L33;
Read: src/main/java/com/northstar/claims/web/tag/FieldTag.java:34-85

**PAY-R29 (v1)** — Every failure in the slice (missing settlement, rejected
insert, missing payment on detail) is answered with HTTP 200 and the global
error page `/WEB-INF/jsp/error.jsp`: title "Claims System Error", the text
"Reference status: null", and a "Return to login" link. No business field is
rendered.
Observed: manual run against localhost Jetty, steps L07, L08, L10, L18, L19, L20, L21, L22;
Read: src/main/webapp/WEB-INF/struts-config.xml:59-60;
src/main/webapp/WEB-INF/web.xml:85-88;
src/main/webapp/WEB-INF/jsp/error.jsp:7-9

**PAY-R30 (v1)** — All four payment screens end with the screen-audit table:
Screen context "Operational record view", Operator = the session user
(`supervisor`), As of date = `report.asof.date` (`2019-04-01`).
Observed: manual run against localhost Jetty, steps L04, L09, L11, L14;
Read: src/main/webapp/WEB-INF/jsp/payment/issue.jsp:26-39;
src/main/webapp/WEB-INF/jsp/payment/history.jsp:35-48;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:26-39;
src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:34-47;
src/main/resources/northstar.properties:2

## Open questions

Code facts that the running app could not show, and behaviours whose business
intent is unknown, are rows OQ-02 to OQ-09 in `docs/specs/OPEN-QUESTIONS.md`.
They are not rules.

## Transcript mapping

The recorded transcripts prove these rules directly; everything else is proved
by the live run in `docs/specs/SPEC-PAY-001-live.json`.

```transcript_rules
payment_issue: PAY-R02, PAY-R14, PAY-R15, PAY-R16, PAY-R17
payment_history: PAY-R18, PAY-R19
```

## Live-run evidence

<!-- live-evidence:begin -->
| Step | Request | Result | Rendered fields |
| --- | --- | --- | --- |
| L01 | `GET /claims/payment/history.do`  | 302 redirect http://localhost:8080/claims/login.do | - |
| L02 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Anon amount=1 paymentMethod=CHECK` | 302 redirect http://localhost:8080/claims/login.do | - |
| L03 | `POST /claims/login.do` `username=supervisor password=supervisor` | 200 forward /WEB-INF/jsp/home.jsp | signedInUser=supervisor |
| L04 | `GET /claims/payment/history.do`  | 200 forward /WEB-INF/jsp/payment/history.jsp | - |
| L05 | `GET /claims/payment/history.do` `claimId=1` | 200 forward /WEB-INF/jsp/payment/history.jsp | checkNumber_1=CHK-00001, paymentAmount_1=500.00, paymentStatus_1=ISSUED |
| L06 | `GET /claims/payment/history.do` `claimId=abc` | 200 forward /WEB-INF/jsp/payment/history.jsp | - |
| L07 | `GET /claims/payment/detail.do`  | 200 error.jsp (Reference status: null) | - |
| L08 | `GET /claims/payment/detail.do` `paymentId=9999` | 200 error.jsp (Reference status: null) | - |
| L09 | `GET /claims/payment/detail.do` `paymentId=1` | 200 forward /WEB-INF/jsp/payment/detail.jsp | detailPaymentId=1, detailPaymentClaim=1, detailPaymentAmount=500.00, detailPaymentCheck=CHK-00001, ... (6 fields) |
| L10 | `GET /claims/payment/detail.do` `paymentId=abc` | 200 error.jsp (Reference status: null) | - |
| L11 | `GET /claims/payment/remittance.do`  | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitClaimId=119, remitCount=0, remitTotal=0.00 |
| L12 | `GET /claims/payment/remittance.do` `claimId=1` | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitCheck_1=CHK-00001, remitDate_1=2019-03-15, remitAmount_1=500.00, remitStatus_1=ISSUED, ... (7 fields) |
| L13 | `GET /claims/payment/remittance.do` `claimId=abc` | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitClaimId=119, remitCount=0, remitTotal=0.00 |
| L14 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Live Payee paymentMethod=wire` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=1118.00, checkNumber=CHK-61, paymentStatus=ISSUED |
| L15 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Second Payee amount=abc paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=1118.00, checkNumber=CHK-62, paymentStatus=ISSUED |
| L16 | `POST /claims/payment/issue.do` `claimId=abc payeeName=Third Payee amount=12.345 paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=12.35, checkNumber=CHK-63, paymentStatus=ISSUED |
| L17 | `POST /claims/payment/issue.do` `claimId=120 payeeName=Zero Payee paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=0.00, checkNumber=CHK-64, paymentStatus=ISSUED |
| L18 | `POST /claims/payment/issue.do` `claimId=9999 payeeName=Nobody amount=1 paymentMethod=CHECK` | 200 error.jsp (Reference status: null) | - |
| L19 | `POST /claims/payment/issue.do` `claimId=118 amount=1 paymentMethod=CHECK` | 200 error.jsp (Reference status: null) | - |
| L20 | `POST /claims/payment/issue.do` `claimId=118 payeeName=No Method amount=1` | 200 error.jsp (Reference status: null) | - |
| L21 | `POST /claims/payment/issue.do` `claimId=118 payeeName=PPPPPPPPPPPPPPPPPPPPP... amount=1 paymentMethod=CHECK` | 200 error.jsp (Reference status: null) | - |
| L22 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Long Method amount=1 paymentMethod=MMMMMMMMMMMMMMMMMMMMM` | 200 error.jsp (Reference status: null) | - |
| L23 | `POST /claims/payment/issue.do` `claimId=118 payeeName= amount=1 paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=1.00, checkNumber=CHK-65, paymentStatus=ISSUED |
| L24 | `GET /claims/payment/remittance.do` `claimId=118` | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitCheck_61=CHK-61, remitDate_61=2019-04-03, remitAmount_61=1118.00, remitStatus_61=ISSUED, ... (15 fields) |
| L25 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Negative Payee amount=-5 paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=-5.00, checkNumber=CHK-66, paymentStatus=ISSUED |
| L26 | `GET /claims/payment/issue.do` `claimId=118 payeeName=Get Payee amount=7 paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=7.00, checkNumber=CHK-67, paymentStatus=ISSUED |
| L27 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Sci Payee amount=1e3 paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=1000.00, checkNumber=CHK-68, paymentStatus=ISSUED |
| L28 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Comma Payee amount=1,000 paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=1118.00, checkNumber=CHK-69, paymentStatus=ISSUED |
| L29 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Space Payee amount= 5  paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=5.00, checkNumber=CHK-70, paymentStatus=ISSUED |
| L30 | `POST /claims/payment/issue.do` `claimId=118 payeeName=QQQQQQQQQQQQQQQQQQQQQ... amount=1 paymentMethod=NNNNNNNNNNNNNNNNNNNN checkNumber=MINE-1` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=1.00, checkNumber=CHK-71, paymentStatus=ISSUED |
| L31 | `POST /claims/payment/issue.do` `claimId=118 payeeName=O'Brien <b>&</b> "Co" amount=2 paymentMethod=ach` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=2.00, checkNumber=CHK-72, paymentStatus=ISSUED |
| L32 | `POST /claims/payment/issue.do` `claimId=118 payeeName=NaN Payee amount=NaN paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=NaN, checkNumber=CHK-73, paymentStatus=ISSUED |
| L33 | `GET /claims/payment/detail.do` `paymentId=61` | 200 forward /WEB-INF/jsp/payment/detail.jsp | detailPaymentId=61, detailPaymentClaim=118, detailPaymentAmount=1118.00, detailPaymentCheck=CHK-61, ... (6 fields) |
| L34 | `GET /claims/payment/detail.do` `paymentId=72` | 200 forward /WEB-INF/jsp/payment/detail.jsp | detailPaymentId=72, detailPaymentClaim=118, detailPaymentAmount=2.00, detailPaymentCheck=CHK-72, ... (6 fields) |
| L35 | `GET /claims/payment/detail.do`  | 200 forward /WEB-INF/jsp/payment/detail.jsp | detailPaymentId=61, detailPaymentClaim=118, detailPaymentAmount=1118.00, detailPaymentCheck=CHK-61, ... (6 fields) |
| L36 | `GET /claims/payment/history.do` `claimId=118` | 200 forward /WEB-INF/jsp/payment/history.jsp | checkNumber_61=CHK-61, paymentAmount_61=1118.00, paymentStatus_61=ISSUED, checkNumber_62=CHK-62, ... (33 fields) |
| L37 | `GET /claims/payment/history.do`  | 200 forward /WEB-INF/jsp/payment/history.jsp | checkNumber_63=CHK-63, paymentAmount_63=12.35, paymentStatus_63=ISSUED |
| L38 | `GET /claims/payment/history.do` `claimId=abc` | 200 forward /WEB-INF/jsp/payment/history.jsp | checkNumber_63=CHK-63, paymentAmount_63=12.35, paymentStatus_63=ISSUED |
| L39 | `GET /claims/payment/remittance.do` `claimId=118` | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitCheck_61=CHK-61, remitDate_61=2019-04-03, remitAmount_61=1118.00, remitStatus_61=ISSUED, ... (47 fields) |
| L40 | `GET /claims/payment/remittance.do` `claimId=119` | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitCheck_63=CHK-63, remitDate_63=2019-04-03, remitAmount_63=12.35, remitStatus_63=ISSUED, ... (7 fields) |
| L41 | `GET /claims/payment/remittance.do` `claimId=120` | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitCheck_64=CHK-64, remitDate_64=2019-04-03, remitAmount_64=0.00, remitStatus_64=ISSUED, ... (7 fields) |
| L42 | `POST /claims/settlement/save.do` `claimId=118 coveredAmount=3000 deductible=250 depreciation=0` | 200 forward /WEB-INF/jsp/settlement/save.jsp | settlementAmount=2750.00, savedBy=supervisor |
| L43 | `POST /claims/payment/issue.do` `claimId=118 payeeName=Latest Payee paymentMethod=CHECK` | 200 forward /WEB-INF/jsp/payment/issue.jsp | paymentAmount=2750.00, checkNumber=CHK-74, paymentStatus=ISSUED |
| L44 | `GET /claims/payment/detail.do` `paymentId=74` | 200 forward /WEB-INF/jsp/payment/detail.jsp | detailPaymentId=74, detailPaymentClaim=118, detailPaymentAmount=2750.00, detailPaymentCheck=CHK-74, ... (6 fields) |
| L45 | `GET /claims/payment/detail.do` `paymentId=73` | 200 forward /WEB-INF/jsp/payment/detail.jsp | detailPaymentId=73, detailPaymentClaim=118, detailPaymentAmount=NaN, detailPaymentCheck=CHK-73, ... (6 fields) |
| L46 | `GET /claims/payment/detail.do` `paymentId=65` | 200 forward /WEB-INF/jsp/payment/detail.jsp | detailPaymentId=65, detailPaymentClaim=118, detailPaymentAmount=1.00, detailPaymentCheck=CHK-65, ... (6 fields) |
| L47 | `GET /claims/payment/remittance.do` `claimId=118` | 200 forward /WEB-INF/jsp/payment/remittance.jsp | remitCheck_61=CHK-61, remitDate_61=2019-04-03, remitAmount_61=1118.00, remitStatus_61=ISSUED, ... (51 fields) |

Read-only JDBC probe after the run (`payment_id | claim_id | settlement_id | [payee_name] | amount | [payment_method] | check_number | issued_date | status`):

```
61 | 118 | 118 | [Live Payee] | 1118.0E0 | [wire] | CHK-61 | 2019-04-03 | ISSUED
62 | 118 | 118 | [Second Payee] | 1118.0E0 | [CHECK] | CHK-62 | 2019-04-03 | ISSUED
63 | 119 | 119 | [Third Payee] | 12.345E0 | [CHECK] | CHK-63 | 2019-04-03 | ISSUED
64 | 120 | 120 | [Zero Payee] | 0.0E0 | [CHECK] | CHK-64 | 2019-04-03 | ISSUED
65 | 118 | 118 | [] | 1.0E0 | [CHECK] | CHK-65 | 2019-04-03 | ISSUED
66 | 118 | 118 | [Negative Payee] | -5.0E0 | [CHECK] | CHK-66 | 2019-04-03 | ISSUED
67 | 118 | 118 | [Get Payee] | 7.0E0 | [CHECK] | CHK-67 | 2019-04-03 | ISSUED
68 | 118 | 118 | [Sci Payee] | 1000.0E0 | [CHECK] | CHK-68 | 2019-04-03 | ISSUED
69 | 118 | 118 | [Comma Payee] | 1118.0E0 | [CHECK] | CHK-69 | 2019-04-03 | ISSUED
70 | 118 | 118 | [Space Payee] | 5.0E0 | [CHECK] | CHK-70 | 2019-04-03 | ISSUED
71 | 118 | 118 | [QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ] | 1.0E0 | [NNNNNNNNNNNNNNNNNNNN] | CHK-71 | 2019-04-03 | ISSUED
72 | 118 | 118 | [O'Brien <b>&</b> "Co"] | 2.0E0 | [ach] | CHK-72 | 2019-04-03 | ISSUED
73 | 118 | 118 | [NaN Payee] | 0E0/0E0 | [CHECK] | CHK-73 | 2019-04-03 | ISSUED
74 | 118 | 121 | [Latest Payee] | 2750.0E0 | [CHECK] | CHK-74 | 2019-04-03 | ISSUED
count=74 max=74
SETTLEMENT 118 claim 118 amount 1118.0E0
SETTLEMENT 119 claim 119 amount 0.0E0
SETTLEMENT 120 claim 120 amount 0.0E0
SETTLEMENT 121 claim 118 amount 2750.0E0
claim118 count=12
```
<!-- live-evidence:end -->

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
| PAY-R27 | v1 | Initial rule, version 0.1 |
| PAY-R28 | v1 | Initial rule, version 0.1 |
| PAY-R29 | v1 | Initial rule, version 0.1 |
| PAY-R30 | v1 | Initial rule, version 0.1 |
