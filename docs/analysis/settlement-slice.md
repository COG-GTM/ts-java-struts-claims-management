# Settlement slice as it works today

Every statement below carries a file reference. Statements are tagged:

* **Observed** — seen while running the app on this machine (`make build`, `make test`,
  `make seed`, `make run`, browser login as `supervisor`, settlement calculation for
  claim 119).
* **Read** — known only from reading the source; not exercised during that run.

## 1. Request path

1. **Read** — The browser reaches the application under the `/claims` context; JSP views
   live under `WEB-INF` so they cannot be requested directly and the only entry points are
   `*.do` actions (`src/main/webapp/WEB-INF/web.xml:60-63`).
2. **Observed** — `http://localhost:8080/claims/` does not render an application page: the
   welcome file is `login.do` (`src/main/webapp/WEB-INF/web.xml:65`) but the directory
   listing is served instead, so the login screen has to be requested as
   `/claims/login.do`.
3. **Read** — `AuthFilter` is mapped to every `*.do` URL
   (`src/main/webapp/WEB-INF/web.xml:77-80`). It passes the request through when the URI is
   public or the session carries a `user` attribute, otherwise it redirects to `login.do`
   (`src/main/java/com/northstar/claims/web/AuthFilter.java:37-42`).
4. **Read** — "Public" is a suffix test over the raw URI: `login.do`, `index.jsp`, `.css`,
   `.gif`, `.jpg` (`src/main/java/com/northstar/claims/web/AuthFilter.java:45-51`).
5. **Observed** — Signing in as `supervisor` / `supervisor` establishes that session marker
   (`src/main/java/com/northstar/claims/web/LoginAction.java:26-27`) and the home screen
   then shows `Operator: supervisor`, read from that attribute
   (`src/main/webapp/WEB-INF/jsp/nav.jsp:10`). The adjacent `Session: Authenticated` line
   is a static resource string, not a live check
   (`src/main/webapp/WEB-INF/jsp/nav.jsp:14`,
   `src/main/resources/ApplicationResources.properties:260`).
6. **Read** — Struts' `ActionServlet` handles `*.do`
   (`src/main/webapp/WEB-INF/web.xml:39-41,60-63`) and dispatches `/settlement/calculate` to
   `SettlementCalculateAction` with the request-scoped `settlementForm` bean and
   `validate="false"`, forwarding `settlement` to
   `/WEB-INF/jsp/settlement/calculate.jsp` (`src/main/webapp/WEB-INF/struts-config.xml:205-213`).
7. **Read** — The form bean holds all four inputs as `String` and validates nothing beyond
   the base hook (`src/main/java/com/northstar/claims/web/form/SettlementForm.java:11-75`).
8. **Read** — `SettlementCalculateAction` ignores that form bean entirely and reads raw
   request parameters instead
   (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23-37`).
9. **Read** — The action resolves the claim through `ClaimDAO`
   (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:93-101`,
   called at `src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:24`),
   then the claim's policy through `PolicyDAO` to obtain the policy limit; if either lookup
   yields nothing the limit silently defaults to `10000`
   (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:25-31`).
10. **Read** — Parameter coercion is lenient: a missing or unparsable `coveredAmount`
    becomes `5000`, `depreciation` becomes `0`, and `claimId` becomes `119`
    (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23,32-33`;
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-46`).
11. **Observed** — Because of those defaults, requesting
    `/claims/settlement/calculate.do?claimId=119` with no amounts still renders a complete
    settlement (covered `5000.00`, deductible `0.00`, amount `1000.00`, capped `true`).
12. **Read** — A `null` or empty `deductible` is converted to the string `"0"` before the
    calculator sees it
    (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:35-37`).
13. **Read** — `SettlementCalculator` is a lazily created, `synchronized`-accessor
    singleton (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:12-20`).
14. **Read** — The arithmetic is, in order: `gross = covered - depreciation`;
    `afterDeductible = gross - deductible`; floor at `0`; `capped = afterDeductible > policyLimit`;
    `amount = capped ? policyLimit : afterDeductible`; `rounded = Math.round(amount * 100.0) / 100.0`
    (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:26-37`).
15. **Read** — The rounding is applied only to the settlement amount. `coveredAmount`,
    `deductibleApplied` and `depreciation` are stored on the result as the raw doubles that
    came in (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:38-43`).
16. **Read** — The action puts the `Settlement` and the policy limit in request scope and
    returns the `settlement` forward
    (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:38-42`).
17. **Read** — `calculate.jsp` renders the five business values through `<ns:field>` with
    `type="money"` (`src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp:20-28`).
18. **Read** — `FieldTag` emits `<span id="f_NAME">value</span>` and, for `type="money"`,
    formats with `String.format("%.2f", ...)`, falling back to the raw text when the value
    is not numeric (`src/main/java/com/northstar/claims/web/tag/FieldTag.java:34-46,48-59`).
19. **Observed** — Consequently the boolean `cappedAtLimit`, which is also declared
    `type="money"` (`src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp:26`), renders as
    the literal `true` rather than a formatted number.
20. **Observed** — The "Settlement detail" link renders as `detail.do?claimId=` with an
    empty id: the JSP reads `${claimId}`
    (`src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp:17`) but
    `SettlementCalculateAction` never sets a `claimId` request attribute — it only sets the
    id on the `Settlement` bean
    (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:38-41`).
21. **Observed** — For claim 119 with covered `5000.00`, deductible `500.00`, depreciation
    `0.00`, the screen showed **settlement amount `1000.00`, capped `true`** — gross `4500`
    clipped to policy 9001's `1000` limit
    (`src/main/resources/db/seed.sql:49`,
    `src/main/java/com/northstar/claims/service/SettlementCalculator.java:35-36`).
22. **Read** — Saving is a separate action: `/settlement/save` maps to
    `SettlementSaveAction` and forwards to `/WEB-INF/jsp/settlement/save.jsp`
    (`src/main/webapp/WEB-INF/struts-config.xml:214-222`).
23. **Read** — The save action recomputes the settlement from the request parameters rather
    than reusing anything from the calculate request — there is no server-side carry-over
    between the two screens
    (`src/main/java/com/northstar/claims/web/SettlementSaveAction.java:24-33`).
24. **Read** — The saved row's `calculated_by` comes from the session `user` attribute and
    `calculated_date` is the hard-coded string `2019-04-01`
    (`src/main/java/com/northstar/claims/web/SettlementSaveAction.java:36-38`).
25. **Read** — `SettlementDetailAction` is the only settlement screen that reads a stored
    settlement back (`src/main/java/com/northstar/claims/web/SettlementDetailAction.java:16-23`).

## 2. Tables read and written

26. **Read** — `SETTLEMENT` stores `settlement_id`, `claim_id`, `covered_amount`,
    `deductible_applied`, `depreciation`, `capped_at_limit`, `settlement_amount`,
    `calculated_by`, `calculated_date`, keyed on `settlement_id` with a foreign key to
    `CLAIM` (`src/main/resources/db/schema.sql:92-104`).
27. **Read** — Money columns are SQL `DOUBLE`, not `DECIMAL`
    (`src/main/resources/db/schema.sql:95-99`), matching the `double` arithmetic in the
    calculator (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:23-37`).
28. **Read** — `/settlement/calculate` **reads** `CLAIM` (to find the policy) and `POLICY`
    (for `policy_limit`) and **writes nothing**
    (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:24-31`;
    `src/main/resources/db/schema.sql:13-27,51-68`).
29. **Read** — `/settlement/save` reads the same two tables and **inserts** one `SETTLEMENT`
    row via a prepared statement
    (`src/main/java/com/northstar/claims/web/SettlementSaveAction.java:25-39`;
    `src/main/java/com/northstar/claims/dao/SettlementDAO.java:118-141`).
30. **Read** — The new primary key is `max(settlement_id)+1` read in a separate connection
    with no locking or uniqueness guard, so two concurrent saves can collide
    (`src/main/java/com/northstar/claims/web/SettlementSaveAction.java:34`;
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-71`).
31. **Read** — The save path uses two different connection mechanisms in one request:
    `nextId` opens a raw `DriverManager` connection to `claims.db.path`
    (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:24-30`) while
    `SettlementDAO` goes through `ConnectionPool`, which reuses a pooled connection, then
    tries `java:comp/env/jdbc/ClaimsDB`, and finally falls back to `DriverManager`
    (`src/main/java/com/northstar/claims/dao/ConnectionPool.java:35-48`).
32. **Read** — `/settlement/detail` reads `SETTLEMENT` only, taking the highest
    `settlement_id` for the claim
    (`src/main/java/com/northstar/claims/dao/SettlementDAO.java:100-113`).
33. **Read** — `PAYMENT` references `SETTLEMENT` by foreign key, so saved settlements are
    what downstream payments hang off (`src/main/resources/db/schema.sql:106-119`).

### Seed rows involved

34. **Read** — Policy `9001` ("Cap Trap Policy", AUTO) has `policy_limit = 1000` and
    `deductible = 100` (`src/main/resources/db/seed.sql:49`).
35. **Read** — Policy `9002` ("Deductible Trap Policy", HOMEOWNERS) has
    `policy_limit = 100000` and `deductible = 5000` (`src/main/resources/db/seed.sql:50`).
36. **Read** — Claim `119` (`CLM-00119`, status `DENIED`, reserve `1500`) belongs to policy
    `9001`, which is why almost any realistic covered amount caps at `1000`
    (`src/main/resources/db/seed.sql:253`).
37. **Read** — Claim `120` (`CLM-00120`, status `CLOSED`, reserve `2000`) belongs to policy
    `9002`, whose `100000` limit means the cap effectively never binds there
    (`src/main/resources/db/seed.sql:254`).
38. **Read** — Seeded settlement `119` is `covered 1500, deductible 500, depreciation 100,
    capped FALSE, amount 0` — an amount that is *not* what the calculator would produce from
    those inputs (`src/main/resources/db/seed.sql:613`;
    `src/main/java/com/northstar/claims/service/SettlementCalculator.java:30-37`).
39. **Read** — Seeded settlement `120` is `covered 2000, deductible 5000, depreciation 100,
    capped FALSE, amount 0` (`src/main/resources/db/seed.sql:614`).
40. **Read** — Neither settlement screen enforces claim status, so settlements are
    calculable against the `DENIED` claim 119 and the `CLOSED` claim 120
    (`src/main/resources/db/seed.sql:253-254`;
    `src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23-42`).
41. **Observed** — Claim 119's calculation used policy 9001's `1000` limit exactly as
    seeded (`src/main/resources/db/seed.sql:49,253`).

## 3. Settlement transcripts

42. **Read** — Transcripts are produced by `tools/capture/capture.py`, which resets the
    database, starts Jetty, logs in, replays a fixed scenario list, and records status,
    forward, `<ns:field>` values and probe results
    (`tools/capture/capture.py:26-120,200-244`).

| Transcript | Claim / policy limit | Input (covered, deductible, depreciation) | Output |
| --- | --- | --- | --- |
| `transcripts/settlement_calculate.json` | 119 / 1000 | 5000.00, 500.00, 0.00 | amount `1000.00`, capped `true` |
| `transcripts/settlement_policy_cap.json` | 119 / 1000 | 20000.00, 100.00, 0.00 | amount `1000.00`, capped `true` |
| `transcripts/settlement_blank_deductible.json` | 120 / 100000 | 5000.00, `""`, 500.00 | amount `4500.00`, deductible applied `0.00`, capped `false` |
| `transcripts/settlement_deductible_floor.json` | 120 / 100000 | 1000.00, 2000.00, 0.00 | amount `0.00`, capped `false` |
| `transcripts/settlement_half_cent.json` | 120 / 100000 | 1.005, `""`, 0.00 | amount `1.00`, covered shown as `1.01`, capped `false` |
| `transcripts/settlement_save.json` | 119 / 1000 | 5000.00, 500.00, 0.00 | amount `1000.00`, `savedBy supervisor`, forward `save.jsp` |

43. **Read** — All five calculate transcripts expect HTTP 200, no validation errors, and the
    forward `/WEB-INF/jsp/settlement/calculate.jsp`
    (`transcripts/settlement_calculate.json`, `transcripts/settlement_policy_cap.json`,
    `transcripts/settlement_blank_deductible.json`,
    `transcripts/settlement_deductible_floor.json`, `transcripts/settlement_half_cent.json`).
44. **Read** — `settlement_blank_deductible.json` records an unrelated probe
    (`claim.120.status = CLOSED`) because the scenario declares one; the settlement request
    itself changes no state (`transcripts/settlement_blank_deductible.json`;
    `tools/capture/capture.py:72-79,220-244`).
45. **Read** — `settlement_save.json` is the only settlement transcript with a `db_state`
    entry, `settlement.claim.119.amount = 1000.00`
    (`transcripts/settlement_save.json`).
46. **Observed** — My manual run of claim 119 reproduced `settlement_calculate.json`
    exactly: covered `5000.00`, deductible applied `500.00`, depreciation `0.00`,
    capped `true`, amount `1000.00` (`transcripts/settlement_calculate.json`).
47. **Read** — Six of the twenty-two transcripts concern settlement; the remainder cover
    login, policy, intake, workbench, payment and reporting (`transcripts/index.json`).

## 4. What happens to the input `1.005`

48. **Read** — The action parses `"1.005"` with `Double.parseDouble`
    (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:38-45`), and the
    nearest `double` to 1.005 is slightly *below* it (≈1.00499999999999989).
49. **Read** — The calculator computes `Math.round(1.005 * 100.0) / 100.0`. The product is
    `100.49999999999999`, `Math.round` yields `100`, and the result is `1.0` — the
    settlement amount is `1.00`, i.e. it rounds **down**, not half-up
    (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:37`).
50. **Read** — That behaviour is pinned as intentional by the unit test
    `halfCentUsesLegacyDoubleMath`, which asserts `1.0`
    (`src/test/java/com/northstar/claims/SettlementCalculatorTest.java:41-44`).
51. **Read** — The displayed *covered amount* takes a different path: it is not rounded by
    the calculator (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:39`),
    so the JSP passes `1.005` to `FieldTag`, whose `String.format("%.2f", ...)` rounds the
    shortest decimal representation half-up and prints `1.01`
    (`src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-58`).
52. **Read** — The transcript captures exactly that split — covered `1.01` against
    settlement `1.00` — so a single request rounds the same input in two directions
    (`transcripts/settlement_half_cent.json`).
53. **Read** — If such a value were saved, the unrounded `1.005` would be persisted into the
    `DOUBLE` `covered_amount` column while the rounded `1.00` goes into `settlement_amount`
    (`src/main/java/com/northstar/claims/dao/SettlementDAO.java:130-134`;
    `src/main/resources/db/schema.sql:95-99`).
54. **Observed** — `make test` ran 9 tests (6 in `SettlementCalculatorTest`, 3 in
    `DaoIntegrationTest`) with no failures or errors, so the rounding rule above is green on
    this machine (`src/test/java/com/northstar/claims/SettlementCalculatorTest.java`).

## 5. Checking two documented claims

### `docs/ARCHITECTURE.txt`: "Most actions extend WorkflowAction" — **does not hold**

55. **Read** — The claim appears at `docs/ARCHITECTURE.txt:9-11`.
56. **Read** — No class named `WorkflowAction` exists anywhere in the source tree; the only
    occurrence of the name in the repository is that sentence in `docs/ARCHITECTURE.txt:10`.
57. **Read** — All 37 concrete actions in `src/main/java/com/northstar/claims/web/` extend
    `ClaimsActionSupport`, which itself extends Struts' `Action`
    (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:20`), including the
    settlement three (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:18`,
    `src/main/java/com/northstar/claims/web/SettlementSaveAction.java:19`,
    `src/main/java/com/northstar/claims/web/SettlementDetailAction.java:12`).
58. **Read** — The related sentence that "a few older actions still use their own DAO calls
    and connection handling" (`docs/ARCHITECTURE.txt:11-12`) understates the situation: the
    shared base class itself is the one holding raw `DriverManager` and string-SQL helpers
    (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:24-30,61-86`).

### `README.md`: the build targets Java 7 — **holds for the build, but README.md does not actually say it**

59. **Read** — `README.md` never mentions Java or a JDK version; its build section only
    lists the `make` targets (`README.md`, "Build and run"). The Java 7 target is stated in
    the build file, not the README.
60. **Read** — `pom.xml` sets `maven.compiler.source`/`target` to `7` (`pom.xml:13-14`) and
    repeats `<source>7</source><target>7</target>` on `maven-compiler-plugin`
    (`pom.xml:52-53`).
61. **Observed** — The compiled output confirms it: `target/classes` class files carry major
    version `51` (Java 7 bytecode) after `make build`, produced by JDK 11 — which is the
    source of the bootstrap-classpath warnings (`pom.xml:52-53`).
62. **Read** — `source`/`target` is used rather than `release`, so Java 7 bytecode is emitted
    against the JDK 11 class library; there is no toolchain or `animal-sniffer` check
    binding the API surface to Java 7 (`pom.xml:13-14,52-53`).

## What is not visible from transcripts alone

63. **Read** — The `settlement_save` transcript's `db_state` probe does not read the
    database. `capture.py` resolves `settlement.claim.*` by re-requesting
    `/settlement/calculate.do?claimId=119` and scraping the `settlementAmount` span
    (`tools/capture/capture.py:52-56,181-182`). That probe URL carries no amounts, so it
    re-runs the calculator on the defaults (covered `5000`, deductible `0`, limit `1000`)
    and returns `1000.00` whether or not the insert happened
    (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23-37`). The
    transcript would look identical if the `SETTLEMENT` insert silently failed.
64. **Read** — Nothing in the transcripts reveals that the saved `calculated_date` is the
    frozen literal `2019-04-01`, or that `calculated_by` is a `String.valueOf` of a possibly
    `null` session attribute (which would persist the text `null`)
    (`src/main/java/com/northstar/claims/web/SettlementSaveAction.java:36-38`).
65. **Read** — The `max(id)+1` key allocation and its concurrency hazard are invisible to a
    single-threaded replay (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:61-71`).
66. **Read** — The two-connection-mechanism split inside one save request — `DriverManager`
    for the id, `ConnectionPool` (JNDI then fallback) for the insert — cannot be seen from
    HTTP output (`src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:24-30`;
    `src/main/java/com/northstar/claims/dao/ConnectionPool.java:35-48`).
67. **Read** — The defaulting rules are hidden because every transcript supplies complete
    inputs: garbage or missing amounts silently become `5000`/`0`, a missing `claimId`
    becomes `119`, and a missing claim or policy yields a `10000` limit rather than an error
    (`src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23-33`;
    `src/main/java/com/northstar/claims/web/ClaimsActionSupport.java:31-46`).
68. **Read** — `SettlementForm` and its `validate` hook are declared in the mapping but the
    action never consults the bean, and `validate="false"` disables the hook anyway, so no
    transcript can distinguish "validation passed" from "validation never ran"
    (`src/main/webapp/WEB-INF/struts-config.xml:205-211`;
    `src/main/java/com/northstar/claims/web/form/SettlementForm.java:71-74`;
    `src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23-37`).
69. **Read** — The transcripts show `1.01` next to `1.00` but not *why*: the divergence comes
    from two different rounding implementations — `Math.round`-on-`double` in the service
    versus `String.format("%.2f")` in the tag
    (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:37`;
    `src/main/java/com/northstar/claims/web/tag/FieldTag.java:52-58`).
70. **Read** — That the covered/deductible/depreciation values are echoed back *unrounded*
    into the bean, and therefore into the database on save, is only visible in the setter
    sequence (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:38-43`;
    `src/main/java/com/northstar/claims/dao/SettlementDAO.java:130-134`).
71. **Observed** — The broken `detail.do?claimId=` link is not captured by any transcript,
    because `capture.py` extracts only `<ns:field>` spans and the forward path, not anchor
    hrefs (`tools/capture/capture.py:21-23,135-142,220-244`;
    `src/main/webapp/WEB-INF/jsp/settlement/calculate.jsp:17`).
72. **Read** — The absence of any claim-status guard is invisible: transcripts happen to use
    a `DENIED` and a `CLOSED` claim and pass, which reads as approval rather than as a
    missing check (`src/main/resources/db/seed.sql:253-254`;
    `src/main/java/com/northstar/claims/web/SettlementCalculateAction.java:23-42`).
73. **Read** — The singleton lifecycle of `SettlementCalculator` — one instance shared by all
    requests, created under a synchronized accessor — is a code-level fact with no HTTP
    signature (`src/main/java/com/northstar/claims/service/SettlementCalculator.java:12-20`).
