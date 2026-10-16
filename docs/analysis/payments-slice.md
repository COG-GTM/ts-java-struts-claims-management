# Payment slice as it works today

Every statement below carries a citation. Statements are tagged:

* **Observed** — seen while driving the running legacy app on this machine (Java 11,
  `make seed`, `make run`, signed in as `supervisor`). Steps `L01`–`L29` are the exact
  requests and responses recorded by `tools/analysis/payments_live.py` in
  `docs/analysis/payments-live.json`; the database rows they left behind were read
  afterwards, read-only, by `tools/analysis/PayDbDump.java` into
  `docs/analysis/payments-db-after.txt`.
* **Read** — known only from reading the source or a transcript; not exercised beyond
  what the cited steps show.

The interactive twin of this file is `docs/analysis/payments-slice.html`
(regenerate with `python3 tools/analysis/build_payments_html.py`). It draws the flow
below as a clickable diagram and opens the cited source lines, transcripts and live
requests next to each claim.

## Summary

* Issuing a payment is one unguarded request: any signed-in GET or POST to
  `/payment/issue.do` writes an `ISSUED` check, with no form, approval, review, claim-status
  check or amount bound (findings 5, 7, 21, 22, 27, 31).
* Missing or bad input is replaced silently: claim → 119, amount → the newest settlement's
  amount (`0.00` on a fresh seed), check number → `CHK-<id>` whatever was sent (findings 9,
  18, 19, 25, 26).
* Missing payee, method or settlement, and concurrent issues, end on an error page that
  answers HTTP 200 (findings 14, 17, 33, 52).
* The read screens default to claim 119 and payment 61; the history "Payment detail" link is
  hard-coded to payment 61 (findings 35, 37, 38, 39).
* Of 26 documented statements about the slice, 13 are contradicted by the code or the live
  run, 12 are confirmed and 1 cannot be checked here (section 8).
* The two payment transcripts show none of this: they send one valid POST and read
  "db_state" by scraping a screen (findings 57, 58).

Precondition for every live step: a fresh `make seed` (60 seeded payments, ids 1–60;
seeded settlement 119 has amount `0`), then `make run`. Payment ids are `max+1`, so the
ids quoted below only hold for that starting state.

## 1. Entry points and access

1. **Read** — The slice has four Struts mappings, all in the default module:
   `/payment/issue` (`PaymentIssueAction`, form bean `paymentForm`, `validate="false"`,
   forward `payment` → `issue.jsp`), `/payment/history` (`PaymentHistoryAction` →
   `history.jsp`), `/payment/detail` (`PaymentDetailAction` → `detail.jsp`) and
   `/payment/remittance` (`PaymentRemittanceAction` → `remittance.jsp`)
   (`src/main/webapp/WEB-INF/struts-config.xml:223-238`,
   `src/main/webapp/WEB-INF/struts-config.xml:340-355`).
2. **Read** — Every `*.do` URL passes `AuthFilter`, which lets a request through only when
   the URI is public or the session holds `user`, otherwise it redirects to `login.do`
   (`src/main/webapp/WEB-INF/web.xml:77-80`,
   `src/main/java/com/northstar/claims/web/AuthFilter.java:37-51`).
3. **Observed** — Unauthenticated `GET /claims/payment/history.do` answers `302` to
   `/claims/login.do` (`L01`).
4. **Read** — The only menu entry into the slice is the "Payment History" quick link,
   which carries no `claimId` (`src/main/webapp/WEB-INF/jsp/nav.jsp:6`,
   `src/main/resources/ApplicationResources.properties:38`).
5. **Read** — No screen links to or posts to `/payment/issue.do`: no JSP in the
   application contains an issue form or an `issue.do` URL, and `issue.jsp` itself is the
   confirmation page that shows the result (`src/main/webapp/WEB-INF/jsp/payment/issue.jsp:16-25`,
   `src/main/webapp/WEB-INF/jsp/nav.jsp:3-15`). Payments can only be issued by a direct
   HTTP request.
6. **Read** — Nothing restricts the HTTP method. The mapping has no method attribute and
   the action reads `request.getParameter`, which serves query-string and form values alike
   (`src/main/webapp/WEB-INF/struts-config.xml:223-231`,
   `src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-32`).
7. **Observed** — A plain `GET /claims/payment/issue.do?claimId=119&payeeName=Get+Payee&paymentMethod=CHECK`
   issued payment 61 and wrote it to `PAYMENT` (`L10`, `docs/analysis/payments-db-after.txt`).
   A link, a bookmark, a browser prefetch or a reload of that URL issues another check.

## 2. Issuing a payment: request → SQL → table

8. **Read** — `PaymentIssueAction` ignores the `paymentForm` bean Struts populates and reads
   raw parameters instead (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-32`,
   `src/main/java/com/northstar/claims/web/form/PaymentForm.java:13-16`).
9. **Read** — `claimId` goes through `integer(..., 119)`: a missing or unparsable value
   silently becomes claim 119 (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22`,
   `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-37`).
10. **Observed** — `claimId=abc` issued payment 68 against claim 119 (`L20`,
    `docs/analysis/payments-db-after.txt`).
11. **Read** — SQL 1: the action loads the newest settlement of the claim,
    `select * from SETTLEMENT where claim_id = ? order by settlement_id desc`, taking the
    first row or `null` (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23`,
    `src/main/java/com/northstar/claims/dao/SettlementDAO.java:99-115`).
12. **Read** — SQL 2: the new id is `select coalesce(max(payment_id),0)+1 from PAYMENT`, run on
    its own `DriverManager` connection, outside any transaction with the insert
    (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:25`,
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:24-29`,
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75`).
13. **Read** — The settlement reference is copied without a null check:
    `settlement.getSettlementId()` (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:28`).
14. **Observed** — Claim 999 has no settlement; the request ends on the error page and no row
    is written (`L19`; the next successful issue, `L20`, still got id 68 after `L16`'s 67).
15. **Read** — `payeeName` and `paymentMethod` are copied raw, with no trimming, defaulting or
    upper-casing. The base class has `normalizeMethod` (blank → `CHECK`, upper-case) but no
    payment action calls it (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:29`,
    `src/main/java/com/northstar/claims/web/PaymentIssueAction.java:32`,
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:253-258`).
16. **Observed** — `paymentMethod=wire` is stored as `wire` (`L16`,
    `docs/analysis/payments-db-after.txt`).
17. **Observed** — A missing `payeeName` or a missing `paymentMethod` ends on the error page
    and writes nothing: both columns are `NOT NULL` (`L17`, `L18`,
    `src/main/resources/db/schema.sql:110`, `src/main/resources/db/schema.sql:112`).
18. **Read** — `amount` goes through `decimal(..., settlement.getSettlementAmount())`: a
    missing or unparsable amount silently becomes the newest settlement's amount
    (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31`,
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:39-45`).
19. **Observed** — On a fresh seed that fallback is `0.00`, because seeded settlement 119 has
    amount `0`: the GET in `L10` and `amount=abc` in `L15` both issued `0.00` checks
    (`src/main/resources/db/seed.sql:613`, `L10`, `L15`).
20. **Observed** — After `POST /settlement/save.do` wrote settlement 121 (`1000.00`) for claim
    119 (`L22`), an issue with no amount paid `1000.00` and referenced settlement 121, so
    "newest" means highest `settlement_id` (`L23`, `docs/analysis/payments-db-after.txt`).
21. **Read** — The amount has no bounds check. `financialAmount` (`0 <= amount < 100000000`)
    exists in the base class but no payment action calls it; nothing compares the amount
    with the settlement, the policy limit or what was already paid
    (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31`,
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:328-330`).
22. **Observed** — `amount=-50` issued a `-50.00` check (`L12`); `amount=250000` issued a
    `250000.00` check against claim 119, whose policy 9001 has a `1000` limit and whose newest
    settlement was `0` (`L14`, `src/main/resources/db/seed.sql:49`,
    `src/main/resources/db/seed.sql:613`).
23. **Observed** — The amount is stored unrounded: `amount=1.005` is persisted as `1.005` in
    the `DOUBLE` column and every screen shows `1.01` (`L13`, `L26`,
    `docs/analysis/payments-db-after.txt`, `src/main/resources/db/schema.sql:111`).
24. **Read** — The `1.01` comes from `FieldTag`'s `String.format("%.2f", ...)`; there is no
    rounding step in the payment code at all
    (`src/main/java/com/northstar/claims/web/tag/FieldTag.java:48-59`,
    `src/main/java/com/northstar/claims/web/PaymentIssueAction.java:30-31`).
25. **Read** — The check number is always `"CHK-" + paymentId`, unpadded; the `checkNumber`
    property on the form bean is never read (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33`,
    `src/main/java/com/northstar/claims/web/form/PaymentForm.java:53-61`).
26. **Observed** — Posting `checkNumber=MY-CHECK-1` produced `CHK-62` (`L11`,
    `docs/analysis/payments-db-after.txt`). Seeded payments use a different, zero-padded
    format, `CHK-00001` … `CHK-00060` (`src/main/resources/db/seed.sql:615-674`).
27. **Read** — `issued_date` is the literal `2019-04-03` and `status` the literal `ISSUED`;
    there is no other status anywhere in the slice
    (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:34-35`).
28. **Observed** — Detail for payment 61 shows issued date `2019-04-03`
    (`L25`); the application's report date is `2019-04-01`, a different literal
    (`src/main/resources/northstar.properties:2`).
29. **Read** — SQL 3: one prepared `insert into PAYMENT (payment_id, claim_id, settlement_id,
    payee_name, amount, payment_method, check_number, issued_date, status) values (?,?,?,?,?,?,?,?,?)`
    through `ConnectionPool` (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:36`,
    `src/main/java/com/northstar/claims/dao/PaymentDAO.java:139-164`).
30. **Read** — The action does not check claim status
    (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-43`).
31. **Observed** — Checks were issued against the `DENIED` claim 119 (`L11`) and the `CLOSED`
    claim 120 (`L21`) (`src/main/resources/db/seed.sql:253-254`).
32. **Read** — The confirmation screen gets five request attributes — `paymentId`, `claimId`,
    `paymentAmount`, `checkNumber`, `paymentStatus` — but `issue.jsp` renders only amount, check
    number and status; payee, method and claim are not shown
    (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:37-43`,
    `src/main/webapp/WEB-INF/jsp/payment/issue.jsp:16-25`).
33. **Observed** — Eight simultaneous issues for claim 2 produced five payments (`CHK-71` …
    `CHK-75`) and three error pages (`L28`, `L29`). This is consistent with two requests
    reading the same `max+1` id and the primary key rejecting the second insert
    (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75`,
    `src/main/resources/db/schema.sql:116`); the error page does not say why.
34. **Read** — Two connection mechanisms serve one issue request: the id from a raw
    `DriverManager` connection, the settlement read and the insert from `ConnectionPool`,
    which reuses a released connection, else tries JNDI `java:comp/env/jdbc/ClaimsDB`, else
    opens `DriverManager` (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:24-29`,
    `src/main/java/com/northstar/claims/dao/ConnectionPool.java:35-49`).

## 3. Read screens: history, detail, remittance

35. **Read** — History: `claimId` defaults to 119; SQL `select * from PAYMENT where claim_id = ?
    order by payment_id`; the screen lists check number, amount and status
    (`src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20-26`,
    `src/main/java/com/northstar/claims/dao/PaymentDAO.java:99-119`,
    `src/main/webapp/WEB-INF/jsp/payment/history.jsp:21-33`).
36. **Observed** — The nav link (no `claimId`) shows claim 119's history, which is empty on a
    fresh seed (`L03`); `?claimId=2` shows seeded `CHK-00002` for `250.00` (`L04`).
37. **Read** — History's "Payment detail" link is the hard-coded `detail.do?paymentId=61`, and
    the whole link paragraph is written twice (`src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19`).
38. **Observed** — Every history page carries that link twice (`L03`, `L04`, `L24`, `L29`), and
    on a fresh seed it opens the error page because payment 61 does not exist yet (`L05`).
39. **Read** — Detail: `paymentId` defaults to 61; SQL `select * from PAYMENT where payment_id = ?`.
    The action guards a missing row when it sets `claimId` (falls back to 119), but the JSP
    then calls `<bean:write name="payment" ...>` on that missing bean
    (`src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24`,
    `src/main/java/com/northstar/claims/dao/PaymentDAO.java:25-44`,
    `src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14`).
40. **Observed** — `detail.do` with no id errors on a fresh seed (`L06`) and shows payment 61
    once it exists (`L25`); `detail.do?paymentId=2` shows the seeded row (`L07`).
41. **Read** — Payee and method are printed with `<bean:write>`, not `<ns:field>`, on detail
    and remittance, so they carry no `f_` span (`src/main/webapp/WEB-INF/jsp/payment/detail.jsp:14`,
    `src/main/webapp/WEB-INF/jsp/payment/detail.jsp:18`,
    `src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:19`).
42. **Observed** — The detail response for payment 2 has `f_` spans for id, claim, amount, check,
    date and status only (`L07`).
43. **Read** — Remittance: `claimId` defaults to 119; it runs the history query again plus
    `select coalesce(sum(amount),0) from PAYMENT where claim_id = ?`, and shows rows, count and
    "Total issued" (`src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:17-24`,
    `src/main/java/com/northstar/claims/dao/PaymentDAO.java:121-137`,
    `src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:16-32`).
44. **Observed** — Remittance for claim 2 on a fresh seed: count `1`, total `250.00` (`L08`);
    with no `claimId`: claim `119`, count `0`, total `0.00` (`L09`).
45. **Observed** — After the issues above, claim 119's "Total issued" is `251966.01`: the sum
    includes the `-50` check, the `250000` check and the raw `1.005`, rounded only for display
    (`L27`, `docs/analysis/payments-db-after.txt`).
46. **Read** — History, detail and remittance write nothing; they all read `PAYMENT` only
    (`src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20-26`,
    `src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24`,
    `src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:17-24`).

## 4. Tables and seed rows

47. **Read** — `PAYMENT` columns: `payment_id`, `claim_id`, `settlement_id`, `payee_name`,
    `amount DOUBLE`, `payment_method VARCHAR(20)`, `check_number VARCHAR(30)`,
    `issued_date DATE`, `status VARCHAR(20)`, all `NOT NULL`; primary key `payment_id`;
    foreign keys to `CLAIM` and `SETTLEMENT` (`src/main/resources/db/schema.sql:106-119`).
48. **Read** — The slice writes only `PAYMENT` and reads only `PAYMENT` and `SETTLEMENT`;
    `CLAIM` is touched only through the foreign key
    (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23-36`,
    `src/main/resources/db/schema.sql:117-118`).
49. **Read** — The seed loads 60 payments, one per claim 1–60, alternating `500` and `250`,
    all `CHECK`, `ISSUED`, `2019-03-15` (`src/main/resources/db/seed.sql:615-674`).
50. **Read** — Outside the slice, the loss-ratio report adds `sum(pay.amount)` from `PAYMENT`
    to each line of business's losses, so negative or oversized checks flow straight into it
    (`src/main/java/com/northstar/claims/dao/ReportDAO.java:55-87`). Not exercised in this run.

## 5. Errors

51. **Read** — Any exception in an action forwards to `error.jsp` through the global
    exception handler (`src/main/webapp/WEB-INF/struts-config.xml:59-60`).
52. **Observed** — Every failure above (`L05`, `L06`, `L17`, `L18`, `L19`, three of `L28`)
    answers HTTP `200` with "Reference status: null": `error.jsp` prints
    `javax.servlet.error.status_code`, which only the container's error-page mechanism sets
    (`src/main/webapp/WEB-INF/jsp/error.jsp:9`). A client cannot tell a failed issue from a
    successful one by status code.
53. **Observed** — Under `make run` the JNDI lookup always fails (`invalid RefAddr: password`),
    and the pool falls back to `DriverManager` on `target/db/northstar`. The Jetty
    datasource omits a password and points at `${java.io.tmpdir}`, not at the seeded file
    (`src/main/webapp/WEB-INF/jetty-env.xml:15-21`,
    `src/main/java/com/northstar/claims/dao/ConnectionPool.java:39-48`,
    `docs/analysis/payments-jetty-console.txt`).

## 6. Transcripts and what they hide

54. **Read** — Two transcripts cover the slice: `payment_issue` (POST claim 119, payee
    "Reserved Claimant", amount `1000.00`, `CHECK` → `CHK-61`, `1000.00`, `ISSUED`) and
    `payment_history` (GET, no parameters → row 61) (`transcripts/payment_issue.json`,
    `transcripts/payment_history.json`).
55. **Read** — `transcripts/index.json` files both under module `settlement`, not `payment`
    (`transcripts/index.json:44-56`).
56. **Read** — `payment_history` only passes because `payment_issue` ran first in the same
    capture: it has no parameters and expects row 61, which the seed does not contain
    (`tools/capture/capture.py:57-65`, `src/main/resources/db/seed.sql:674`).
57. **Read** — The `db_state` values in `payment_issue` are not read from the database.
    `capture.py` gets them by scraping `history.do` spans; `payment.count.claim.119` counts
    `paymentAmount_` spans on `history.do` with no `claimId`, so it is claim 119 only because
    of the default (`tools/capture/capture.py:161-171`, `tools/capture/capture.py:57-63`).
58. **Read** — The transcripts always send a valid amount, payee and method, one request at a
    time, by POST. None of the fallbacks, the GET write, the missing bounds check, the race,
    the ignored `checkNumber` or the hard-coded detail link is visible in them
    (`transcripts/payment_issue.json`, `tools/capture/capture.py:21-23`).
59. **Read** — The only unit-level coverage is `DaoIntegrationTest.readsClaimsAndPayments`,
    which asserts claim 2 has at least one payment
    (`src/test/java/com/northstar/claims/DaoIntegrationTest.java:30-36`).

## 7. Flow map

The HTML twin draws these nodes and edges. Columns: screen → action → SQL → table.
`findings` and `live` say which numbered findings and live steps open as evidence.

| node | column | label | sub | findings | live |
| --- | --- | --- | --- | --- | --- |
| nav | screen | nav.jsp quick link | Payment History, no claimId | 4, 36 | L03 |
| http | screen | Direct HTTP only | no screen posts to issue.do | 5, 6, 7 | L10, L11 |
| issueJsp | screen | issue.jsp | confirmation, 3 fields | 32 | L11 |
| historyJsp | screen | history.jsp | rows + detail link =61 x2 | 35, 37, 38 | L03, L24 |
| detailJsp | screen | detail.jsp | one payment | 39, 40, 41 | L07, L25 |
| remitJsp | screen | remittance.jsp | rows, count, total | 43, 44, 45 | L08, L27 |
| errorJsp | screen | error.jsp | HTTP 200, status null | 51, 52 | L05, L17, L19 |
| auth | action | AuthFilter | every *.do | 2, 3 | L01 |
| issue | action | PaymentIssueAction | /payment/issue.do | 8, 9, 13, 15, 18, 21, 25, 27, 30 | L10, L12, L14, L20 |
| history | action | PaymentHistoryAction | /payment/history.do | 35, 36 | L03, L04 |
| detail | action | PaymentDetailAction | /payment/detail.do | 39, 40 | L06, L25 |
| remit | action | PaymentRemittanceAction | /payment/remittance.do | 43, 44 | L09 |
| qSettle | sql | SettlementDAO.findByClaim | newest settlement by id | 11, 19, 20 | L10, L23 |
| qNextId | sql | nextId("PAYMENT") | max(payment_id)+1, DriverManager | 12, 33, 34 | L28 |
| qInsert | sql | PaymentDAO.insert | 9-column insert | 29, 17, 23 | L11, L17 |
| qByClaim | sql | PaymentDAO.findByClaim | where claim_id = ? order by id | 35 | L24 |
| qById | sql | PaymentDAO.findById | where payment_id = ? | 39 | L25 |
| qTotal | sql | PaymentDAO.totalIssued | coalesce(sum(amount),0) | 43, 45 | L27 |
| tSettlement | table | SETTLEMENT | read only | 11, 20 | L22 |
| tPayment | table | PAYMENT | the only table written | 47, 49 | L29 |
| tClaim | table | CLAIM | foreign key only | 48, 31 | L21 |

| from | to | label | findings |
| --- | --- | --- | --- |
| nav | history | GET, no claimId | 4, 36 |
| http | issue | GET or POST | 5, 6, 7 |
| historyJsp | detail | detail.do?paymentId=61 | 37, 38 |
| historyJsp | remit | remittance.do?claimId= | 43 |
| issue | qSettle | 1 | 11 |
| issue | qNextId | 2 | 12 |
| issue | qInsert | 3 | 29 |
| history | qByClaim | read | 35 |
| detail | qById | read | 39 |
| remit | qByClaim | read | 43 |
| remit | qTotal | read | 43 |
| qSettle | tSettlement | select | 11 |
| qNextId | tPayment | select max | 12 |
| qInsert | tPayment | insert | 29 |
| qInsert | tClaim | FK | 48 |
| qByClaim | tPayment | select | 35 |
| qById | tPayment | select | 39 |
| qTotal | tPayment | sum | 43 |
| issue | issueJsp | forward | 32 |
| history | historyJsp | forward | 35 |
| detail | detailJsp | forward | 39 |
| remit | remitJsp | forward | 43 |
| issue | errorJsp | exception | 14, 17, 33 |
| detail | errorJsp | exception | 40 |

## 8. Documentation vs code

Each statement that the docs, javadoc, screen text or transcripts make about the payment
slice, checked against the code and the live run.

| id | claim | source | verdict | evidence |
| --- | --- | --- | --- | --- |
| D01 | "The default Struts module owns policy, intake, workbench, settlement, payment, and reporting requests." | `docs/ARCHITECTURE.txt:4-7` | Confirmed | The four payment mappings sit in `struts-config.xml`, loaded for the default module (`src/main/webapp/WEB-INF/web.xml:42-45`, `src/main/webapp/WEB-INF/struts-config.xml:223-238`). Findings 1. |
| D02 | "Most actions extend WorkflowAction, which contains the request routing conventions used by the original claims screens." | `docs/ARCHITECTURE.txt:9-11` | Contradicted | No `WorkflowAction` class exists; all four payment actions extend `ClaimsActionSupport` (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:17`, `src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:15`, `src/main/java/com/northstar/claims/web/PaymentDetailAction.java:12`, `src/main/java/com/northstar/claims/web/PaymentRemittanceAction.java:12`). |
| D03 | "A few older actions still use their own DAO calls and connection handling." | `docs/ARCHITECTURE.txt:11-12` | Confirmed | `PaymentIssueAction` opens its own `DriverManager` connection for the id while the DAO uses the pool (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:24-29`, `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-75`). Findings 12, 34. |
| D04 | "ConnectionPool first attempts java:comp/env/jdbc/ClaimsDB, then checks the configured URL entry, and finally opens the HSQLDB file with DriverManager." | `docs/ARCHITECTURE.txt:15-16` | Contradicted | The pool first hands out a released connection, then tries JNDI, then goes straight to `DriverManager`; it never reads the `jdbc/ClaimsDBUrl` entry (`src/main/java/com/northstar/claims/dao/ConnectionPool.java:35-49`, `src/main/webapp/WEB-INF/web.xml:33-38`). Findings 34, 53. |
| D05 | "The pool first asks the servlet container for its declared resource and then falls back to the file database." | `src/main/java/com/northstar/claims/dao/ConnectionPool.java:12-16` | Contradicted | A pooled connection is reused before the container is asked (`src/main/java/com/northstar/claims/dao/ConnectionPool.java:36-38`). Under `make run` the container resource never works (finding 53). |
| D06 | "The current deployment is expected to provide target/db/northstar." | `docs/ARCHITECTURE.txt:17` | Confirmed | Both the pool fallback and `openConnection` default to it (`src/main/java/com/northstar/claims/dao/ConnectionPool.java:25`, `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:24-29`); all live steps ran against it (`docs/analysis/payments-db-after.txt`). |
| D07 | "JSPs ... use include files for the operator header, navigation, and footer." | `docs/ARCHITECTURE.txt:24-25` | Contradicted | `history.jsp` and `issue.jsp` inline their own banner table instead of including `header.jsp`; only `detail.jsp` and `remittance.jsp` include it (`src/main/webapp/WEB-INF/jsp/payment/history.jsp:8-15`, `src/main/webapp/WEB-INF/jsp/payment/issue.jsp:8-15`, `src/main/webapp/WEB-INF/jsp/payment/detail.jsp:5-6`, `src/main/webapp/WEB-INF/jsp/payment/remittance.jsp:6-7`). |
| D08 | "The custom NorthStar tag library supplies field spans used by screen tests and operations support scripts." | `docs/ARCHITECTURE.txt:25-26` | Confirmed | `FieldTag` writes `<span id="f_...">` and `capture.py` reads exactly that (`src/main/java/com/northstar/claims/web/tag/FieldTag.java:34-46`, `tools/capture/capture.py:21-23`). Payee and method have no span (finding 41). |
| D09 | "The default Struts module contains ... settlement, payment, and reporting screens." | `README.md:11-12` | Confirmed | Four payment screens exist (`src/main/webapp/WEB-INF/struts-config.xml:223-238`, `src/main/webapp/WEB-INF/struts-config.xml:340-355`), but none is an input screen for issuing (finding 5). |
| D10 | "Data access is split between raw JDBC DAOs and several direct-JDBC workflow actions." | `README.md:13-14` | Confirmed | The issue path uses both: `PaymentDAO`/`SettlementDAO` and the action's own `nextId` query (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23-36`). |
| D11 | "The application declares a container-managed JNDI datasource but retains a DriverManager fallback"; "DriverManager fallback is expected when the binding is not available." | `README.md:45-47`, `docs/RUNBOOK.txt:85-86` | Confirmed | Observed: every pool connection under `make run` came from the fallback (finding 53; `src/main/java/com/northstar/claims/dao/ConnectionPool.java:44-48`). |
| D12 | Tomcat: configure `jdbc/ClaimsDB` as an HSQLDB data source "user SA, and an empty password"; WebSphere keeps "a file JDBC fallback". | `docs/RUNBOOK.txt:40-42`, `docs/RUNBOOK.txt:52-53` | Not verifiable | No Tomcat or WebSphere here. The Jetty equivalent sets no password and fails (`src/main/webapp/WEB-INF/jetty-env.xml:15-21`). |
| D13 | "Form bean for the PaymentForm screen. String properties preserve the request-bound behavior of the original UI." | `src/main/java/com/northstar/claims/web/form/PaymentForm.java:7-10` | Contradicted | There is no payment input screen, the action never reads the bean, and its `checkNumber` property has no effect (L11) (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:22-33`). Findings 5, 8, 25, 26. |
| D14 | "Handles the PaymentIssueAction request ... The action loads screen data and completes the configured request workflow." | `src/main/java/com/northstar/claims/web/PaymentIssueAction.java:13-16` | Contradicted | Its effect is an insert into `PAYMENT`, on GET as well as POST (L10) (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:36`). Finding 7. |
| D15 | "Inserts a payment record after a check is approved." | `src/main/java/com/northstar/claims/dao/PaymentDAO.java:139` | Contradicted | Nothing approves anything: any signed-in request inserts a row already `ISSUED`, including negative and oversized amounts (L12, L14) (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:35-36`). Findings 21, 27. |
| D16 | "The check is ready for issue control review." | `src/main/resources/ApplicationResources.properties:100` (shown at `src/main/webapp/WEB-INF/jsp/payment/issue.jsp:25`) | Contradicted | The same screen shows status `ISSUED`; no review state or review screen exists (L11) (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:35`). |
| D17 | Screen context: "Operational record view" on the issue confirmation. | `src/main/webapp/WEB-INF/jsp/payment/issue.jsp:28-29`, `src/main/resources/ApplicationResources.properties:243` | Contradicted | The request that renders this page has just written a payment (L11, `docs/analysis/payments-db-after.txt`). |
| D18 | "The settlement is available for payment review." | `src/main/resources/ApplicationResources.properties:95` | Contradicted | Payments do not need a reviewed or newly saved settlement: claim 119's seeded `0` settlement was paid before any save (L10, L11), and there is no payment review step. Findings 19, 27. |
| D19 | "Loads the latest settlement for a claim." | `src/main/java/com/northstar/claims/dao/SettlementDAO.java:99` | Confirmed | `order by settlement_id desc`, first row; after the save, the issue referenced settlement 121 (L23) (`src/main/java/com/northstar/claims/dao/SettlementDAO.java:106-109`). |
| D20 | Nav link "Payment History". | `src/main/webapp/WEB-INF/jsp/nav.jsp:6`, `src/main/resources/ApplicationResources.properties:38` | Contradicted | It shows one claim's history, always claim 119, with no way to choose another on screen (L03) (`src/main/java/com/northstar/claims/web/PaymentHistoryAction.java:20`). |
| D21 | "Payment history is displayed in issue order." | `src/main/resources/ApplicationResources.properties:102` | Confirmed | Rows are ordered by `payment_id`, which is assigned at issue; the race rows appear as 71–75 (L29) (`src/main/java/com/northstar/claims/dao/PaymentDAO.java:107`). |
| D22 | History link "Payment detail". | `src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19`, `src/main/resources/ApplicationResources.properties:278` | Contradicted | Always payment 61, whatever claim is listed, printed twice; on a fresh seed it opens the error page (L05). Findings 37, 38. |
| D23 | "Loads the paymentDetail screen data and selects its configured forward." | `src/main/java/com/northstar/claims/web/PaymentDetailAction.java:11` | Confirmed | Read-only lookup by id (L07), default id 61 (`src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17-24`). |
| D24 | "Calculates total issued money for a claim" / "Total issued". | `src/main/java/com/northstar/claims/dao/PaymentDAO.java:121`, `src/main/resources/ApplicationResources.properties:240` | Confirmed | It sums every payment row for the claim (all are `ISSUED`); negative rows reduce it and the raw `1.005` is included (L27) (`src/main/java/com/northstar/claims/dao/PaymentDAO.java:128`). Finding 45. |
| D25 | Transcript `payment_issue` "db_state": `payment.61.amount`, `payment.61.status`, `payment.count.claim.119`. | `transcripts/payment_issue.json` | Contradicted | These are not database reads: `capture.py` scrapes `history.do` spans, formatted to 2 dp, for the default claim (`tools/capture/capture.py:161-171`). Finding 57. |
| D26 | Transcript description "Issue a settlement payment". | `transcripts/payment_issue.json` | Confirmed | The row references the claim's newest settlement (L23) (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:23-28`), though the amount is not tied to it (L14). |

## 9. Discrepancies and open questions

60. **Read** — Is issuing by GET intended? It is observed (finding 7) but nothing documents
    it (`src/main/webapp/WEB-INF/struts-config.xml:223-231`).
61. **Read** — Should a payment be bounded by the settlement amount, the policy limit or the
    unpaid balance? The code has no bound and an unused `financialAmount` helper
    (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:328-330`). Findings 21, 22.
62. **Read** — Should `DENIED` or `CLOSED` claims accept payments? They do today (finding 31;
    `src/main/resources/db/seed.sql:253-254`).
63. **Read** — Which check-number format is authoritative, the seeded `CHK-00001` or the
    issued `CHK-61`? (`src/main/resources/db/seed.sql:615`,
    `src/main/java/com/northstar/claims/web/PaymentIssueAction.java:33`).
64. **Read** — `detail.do` without an id and the history "Payment detail" link both point at
    payment 61, which exists only after the first issue on a fresh seed; the intended target is
    unknown (`src/main/java/com/northstar/claims/web/PaymentDetailAction.java:17`,
    `src/main/webapp/WEB-INF/jsp/payment/history.jsp:16-19`).
