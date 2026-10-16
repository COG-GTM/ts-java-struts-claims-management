# SPEC-PAY-001 — Payment issue, history, detail and remittance

Version: 0.1

Scope: the four payment request flows of the claims application:
`/claims/payment/issue.do`, `history.do`, `detail.do` and `remittance.do`.
Every rule is taken from the legacy source (`Read:`) and proved against the
running legacy application (`Observed:`). Rule ids are permanent: if a rule
changes, a new version of that id is added and the earlier text is kept.

Each rule carries a status:

* **Observed** — a recorded transcript in `transcripts/` shows the behaviour,
  and the live run repeats it.
* **Read-only** — no transcript covers it; it was read in the source and then
  proved by an exact request and response against the running legacy app.
* **Open question** — not proved; kept out of this spec and listed in
  `docs/specs/OPEN-QUESTIONS.md` (see the last section).

## Evidence

* Transcripts: `payment_issue` and `payment_history` (written by `make capture`).
* Live run: `docs/specs/SPEC-PAY-001-live.json`, written by
  `make payment-spec-proof` (`tools/spec/pay_live_proof.py`). It resets the
  database to the seed exactly as `make capture` does, starts the legacy app on
  port 8080, sends 41 numbered requests (L01 to L41) with full request forms,
  records status, redirect target, forwarded JSP, every `<ns:field>` value and
  the screen text, stops the app, and then reads the PAYMENT and SETTLEMENT rows
  read-only (`tools/spec/PayDbRead.java`). Two runs from the seed write the same
  bytes. `Observed: live L06` below means request L06 of that file.
* Precondition: the live run starts from the seed (60 PAYMENT rows, settlement
  119 for claim 119 with amount 0). The transcripts start later: `make capture`
  runs the settlement save before the payment scenarios, so claim 119 already
  has settlement 121 with amount 1000.00 (live L05 repeats that save).

## Issue a payment

**PAY-R01 (v1)** — A payment is issued by sending `claimId`, `payeeName`,
`amount` and `paymentMethod` to `/claims/payment/issue.do`. The response is
200, forwarded to `/WEB-INF/jsp/payment/issue.jsp`, a confirmation page that
shows only `paymentAmount` (money), `checkNumber` and `paymentStatus`; it shows
no payment id, claim id, payee or method.
Status: Observed.
Observed: payment_issue; live L06.
Read: src/main/webapp/WEB-INF/struts-config.xml:223-231;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-44;
src/main/webapp/WEB-INF/jsp/payment/issue.jsp:16-24

**PAY-R02 (v1)** — `claimId` is parsed as an integer; when it is missing or not
parseable the value 119 is used.
Status: Read-only.
Observed: live L19 (no `claimId`, payment 71 on claim 119); live L20
(`claimId=abc`, payment 72 on claim 119).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37

**PAY-R03 (v1)** — The payment is linked to the claim's settlement with the
highest `settlement_id`; that id is stored in `PAYMENT.settlement_id`.
Status: Read-only.
Observed: live L04 (seed: settlement 119); live L06 (after L05: settlement 121);
live L21 (claim 1: settlement 1); live L22 (claim 120: settlement 120);
`db_after.payment_rows_after_seed` in the live file.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,28;
src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-110

**PAY-R04 (v1)** — `amount` is parsed as a double; when it is missing or not
parseable the `settlement_amount` of the settlement from PAY-R03 is used.
Status: Read-only.
Observed: live L04 (no `amount`, seed settlement 0: `paymentAmount` 0.00);
live L06 (no `amount`: 1000.00); live L07 (`amount=abc`: 1000.00).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45

**PAY-R05 (v1)** — Any amount that parses as a double is stored as sent, with no
validation, no cap and no rounding: a negative amount (`-250.00`), an amount
above the settlement (`5000.00`), `NaN` and exponent notation (`1e3` = 1000)
are all issued. The form is not validated (`validate="false"`). The stored
double is shown with `%.2f`, so `1.005` is stored as 1.005 and shown as 1.01,
and `NaN` is shown as `NaN`.
Status: Read-only.
Observed: live L08, L09, L10, L11, L12; `db_after.payment_rows_after_seed`
(payment 65 amount `1.005E0`, payment 67 amount `0E0/0E0`).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31;
src/main/webapp/WEB-INF/struts-config.xml:223-231;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-58

**PAY-R06 (v1)** — The payment id is `max(payment_id) + 1` over the whole
PAYMENT table, read when the request runs. Every successful request inserts one
new row, so sending the same request twice issues two payments. A request that
fails (PAY-R10, PAY-R11) inserts nothing and uses no id.
Status: Observed.
Observed: payment_issue (payment 61 after the 60 seeded rows); live L04 and L06
(the same form twice: payments 61 and 62); live L19 (payment 71 directly after
payment 70, with the failed L15 to L18 in between);
`db_after.payment_totals` (76 rows, max id 76).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:25-26,36;
src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75

**PAY-R07 (v1)** — The check number is `"CHK-"` followed by the payment id with
no padding (`CHK-61`). A `checkNumber` request parameter is ignored.
Status: Observed.
Observed: payment_issue (`CHK-61`); live L13 (`checkNumber=MY-CHECK-1` gives
`CHK-69`).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33

**PAY-R08 (v1)** — Every issued payment has `issued_date` 2019-04-03 and status
`ISSUED`, whatever the request contains.
Status: Observed.
Observed: payment_issue (`paymentStatus` ISSUED); live L06; live L27
(`detailPaymentDate` 2019-04-03); `db_after.payment_rows_after_seed`.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:34-35

**PAY-R09 (v1)** — `payeeName` and `paymentMethod` are stored exactly as sent:
no trimming, no case change, no check against a list of methods, markup kept.
Status: Read-only.
Observed: live L14 (`paymentMethod=check` stored as `check`); live L23
(`payeeName=<b>A&B</b>` stored as sent); `db_after.payment_rows_after_seed`
(payments 70 and 75).
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:29,32;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:139-164

**PAY-R10 (v1)** — When `paymentMethod` or `payeeName` is missing, or
`paymentMethod` is longer than 20 characters, the insert fails: the response is
200 forwarded to `/WEB-INF/jsp/error.jsp` ("Claims System Error", "Reference
status: null"), no validation message is shown and no row is written.
Status: Read-only.
Observed: live L15, L16, L17; `db_after.payment_totals`.
Read: src/main/resources/db/schema.sql:106-118;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:139-164;
src/main/webapp/WEB-INF/jsp/error.jsp:9

**PAY-R11 (v1)** — When the claim has no settlement row (`claimId=9999`) the
request fails with the same error page as PAY-R10 and no row is written.
Status: Read-only.
Observed: live L18; `db_after.payment_totals`.
Read: src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23,28;
src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-110

**PAY-R12 (v1)** — Issuing is not restricted by HTTP method, claim status or
user: a GET with the same parameters issues a payment; claim 119 (DENIED) and
claim 120 (CLOSED) are paid; a signed-in adjuster (`adjuster7`) can issue.
Status: Read-only.
Observed: live L21 (GET); live L04 (claim 119); live L22 (claim 120); live L35
and L36 (adjuster7 issues `CHK-76`).
Read: src/main/webapp/WEB-INF/struts-config.xml:223-231;
src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-44;
src/main/resources/db/seed.sql:253-254;
src/main/java/com/northstar/claims/web/LoginAction.java:19-26

## Sign-in

**PAY-R13 (v1)** — Without a signed-in session every payment request is
redirected (302) to `/claims/login.do`; an issue request without a session
writes nothing.
Status: Read-only.
Observed: live L01 (history), L38 (issue POST), L39 (detail), L40 (remittance);
`db_after.payment_totals` (76 rows, unchanged by L38).
Read: src/main/java/com/northstar/claims/web/AuthFilter.java:37-50

## Payment history

**PAY-R14 (v1)** — `/claims/payment/history.do` takes `claimId` (missing or not
parseable: 119) and lists that claim's payments in `payment_id` order, one row
each with `checkNumber_<id>`, `paymentAmount_<id>` (money) and
`paymentStatus_<id>`. A claim with no payments gives 200 and an empty list.
Status: Observed.
Observed: payment_history; live L24 (no `claimId`: payments 61 to 72 of claim
119 in id order); live L25 (claim 1: seeded `CHK-00001` 500.00 then `CHK-73`
5.00); live L26 (claim 9999: no rows).
Read: src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20-26;
src/main/java/com/northstar/claims/dao/PaymentDAO.java:99-118;
src/main/webapp/WEB-INF/jsp/payment/history.jsp:26-32

**PAY-R15 (v1)** — The history page always shows two identical "detail" links to
`detail.do?paymentId=61`, whatever the claim, each followed by a link to
`remittance.do?claimId=<claimId>`.
Status: Read-only.
Observed: live L24, L25, L26 (`links` in the live file).
Read: src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19

## Payment detail

**PAY-R16 (v1)** — `/claims/payment/detail.do` takes `paymentId` (missing or
not parseable: 61) and shows payment number, claim number, payee, amount
(money), method, check number, issued date and status.
Status: Read-only.
Observed: live L27 (payment 66); live L28 (no `paymentId`: payment 61); live
L41 (`paymentId=abc`: payment 61).
Read: src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:7-24

**PAY-R17 (v1)** — An unknown `paymentId` gives 200 forwarded to
`/WEB-INF/jsp/error.jsp`.
Status: Read-only.
Observed: live L29 (`paymentId=9999`).
Read: src/main/java/com/northstar/claims/web/PaymentDetailAction.java:18-20;
src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14

**PAY-R18 (v1)** — Payee and method are shown as stored, HTML-escaped: the payee
`<b>A&B</b>` is sent as `&lt;b&gt;A&amp;B&lt;/b&gt;`, and the method `check`
stays lower case.
Status: Read-only.
Observed: live L30 (`payee_markup`); live L31.
Read: src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14,18

## Remittance

**PAY-R19 (v1)** — `/claims/payment/remittance.do` takes `claimId` (missing or
not parseable: 119) and lists that claim's payments in `payment_id` order with
`remitCheck_<id>`, payee, `remitDate_<id>`, `remitAmount_<id>` (money) and
`remitStatus_<id>`, followed by `remitClaimId`, `remitCount` and `remitTotal`.
Status: Read-only.
Observed: live L32 (no `claimId`: claim 119, 12 rows); live L33 (claim 1);
live L37 (claim 120, 3 rows).
Read: src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:17-24;
src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:18-32

**PAY-R20 (v1)** — `remitTotal` is `coalesce(sum(amount), 0)` over every
payment of the claim, whatever its status, shown with `%.2f`: 0.00 when the
claim has no payments, 505.00 for claim 1 (seeded 500 plus 5), 23.00 for claim
120 (20 + 1 + 2), and `NaN` for claim 119 once one of its amounts is NaN.
Status: Read-only.
Observed: live L03, L34 (0.00); live L33 (505.00); live L37 (23.00); live L32
(`NaN`).
Read: src/main/java/com/northstar/claims/dao/PaymentDAO.java:121-136;
src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:22;
src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-58

## Notes on the transcripts

* `transcripts/index.json` files both payment scenarios under module
  `settlement`; they are the payment slice's transcripts by name.
  Read: transcripts/index.json:48-55
* `payment_history` expects payment 61 at 1000.00 because capture issues it
  after the settlement save; on the bare seed the same request gives 0.00
  (PAY-R04, live L04 and L28). Observed: payment_history; live L28.

## Open questions

Not proved, so not rules. Tracked in `docs/specs/OPEN-QUESTIONS.md`:

* OQ-02 — two issue requests at the same moment can read the same
  `max(payment_id)`; whether the second fails or duplicates was not reproduced.
* OQ-03 — whether the fixed issued date 2019-04-03 is meant to be the business
  "as of" date or is a leftover constant.
* OQ-04 — whether negative, NaN and above-settlement amounts (PAY-R05) and the
  fixed `paymentId=61` history links (PAY-R15) are intended behaviour.

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
