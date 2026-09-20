# ADR-001: Extract the settlement module alone into a Spring Boot service

| Field      | Value                                                                                     |
|------------|-------------------------------------------------------------------------------------------|
| Status     | Proposed                                                                                  |
| Date       | 2026-09-20                                                                                |
| Deciders   | Ben Lau (reviewer), NorthStar claims team                                                 |
| Author     | Devin                                                                                     |
| Baseline   | `main` at commit `225c8d3`; behaviour as recorded in `docs/specs/SPEC-SETTLE-001.md` v0.2 |
| Supersedes | none                                                                                      |

Rule citations of the form `SETTLE-Rxx` refer to
`docs/specs/SPEC-SETTLE-001.md`. `OQ-xx` refers to that document's open
questions.

## Context

NorthStar is a single Struts 1.3.10 WAR deployed under `/claims`. The default
Struts module owns policy, intake, workbench, settlement, payment and reporting
(`docs/ARCHITECTURE.txt`). All screens share one file-mode HSQLDB opened by
`ConnectionPool` (`src/main/java/com/northstar/claims/dao/ConnectionPool.java`)
either through the container `jdbc/ClaimsDB` data source or directly from
`target/db/northstar`, and one raw-JDBC data layer.

The settlement module is the narrowest slice of the application that has both
a written behavioural contract and an executable oracle:

- SPEC-SETTLE-001 v0.2 records 51 rules for the three `/settlement/*`
  mappings, of which 24 are `Observed` in transcripts or by execution and 27
  are `Inferred` from source only; 14 business questions are open.
- Six deterministic transcripts under `transcripts/` exercise the module:
  `settlement_calculate`, `settlement_save`, `settlement_blank_deductible`,
  `settlement_half_cent`, `settlement_policy_cap` and
  `settlement_deductible_floor`. `make capture` regenerates them from the
  seeded application and CI (`.github/workflows/`) fails if the regenerated
  files differ, so the transcripts are a stable regression oracle today.

The module's coupling to the rest of the monolith is small and well
understood:

- Inbound data: settlement reads the claim and its policy to obtain the policy
  limit (SETTLE-R14, SETTLE-R49) and the session `user` attribute for the
  operator (SETTLE-R20, SETTLE-R40, SETTLE-R48). It never writes the `CLAIM`
  row (SETTLE-R44, SETTLE-R51).
- Outbound data: settlement writes only the `SETTLEMENT` table
  (SETTLE-R38, SETTLE-R42), allocating ids as `max(settlement_id) + 1`
  (SETTLE-R39).
- Consumers: `PaymentIssueAction` reads `SettlementDAO.findByClaim` to obtain
  the `settlement_id` and default amount for a payment, and
  `PAYMENT.settlement_id` has a foreign key to `SETTLEMENT.settlement_id`
  (`src/main/resources/db/schema.sql`). Nothing else in the monolith reads
  `SETTLEMENT`.
- Presentation: the transcript harness observes the module only through the
  `<!-- ns:view ... -->` marker and the `<span id="f_NAME">` field spans
  (SETTLE-R07, SETTLE-R08, SETTLE-R34, SETTLE-R35), not through the JSP files
  themselves.

The team wants to start decomposing the monolith with a slice that can be
proven equivalent before and after the move, rather than with the slice that
would be most valuable if the migration were free.

## Options considered

### Option 1: settlement alone

Move the three `/settlement/*` mappings, `SettlementCalculator`, the
settlement persistence and the settlement screens into a new Spring Boot
service at `services/settlement-service` on port 8083. Payments, workbench,
reporting, policy and intake stay in the Struts application.

- For: the boundary is the one already drawn by the spec and the six
  transcripts, so the acceptance criterion is mechanical (all six replay
  green). The calculation rules that must be preserved bit-for-bit
  (SETTLE-R21 to SETTLE-R33, in particular the `Math.round(amount * 100.0) /
  100.0` double rounding of SETTLE-R28 and the strict `>` cap of SETTLE-R24
  and SETTLE-R25) are pure and already isolated in one class. The module's
  only writes are to its own table (SETTLE-R38, SETTLE-R44, SETTLE-R51).
- Against: the settlement service still needs read access to `CLAIM` and
  `POLICY` for the limit (SETTLE-R14) and the payment module still needs read
  access to `SETTLEMENT` plus the foreign key, so the two sides share a schema
  for now. The `max + 1` id allocation (SETTLE-R39) must stay in one place or
  it will collide. Accidental behaviours the business has not yet ruled on
  (SETTLE-R11, SETTLE-R12, SETTLE-R49, SETTLE-R18, SETTLE-R29; OQ-01 to OQ-05)
  have to be reproduced as-is until those decisions are made, because the
  transcript probe `settlement.claim.119.amount` (SETTLE-R43) is a GET to
  `/settlement/calculate.do?claimId=119` and therefore depends on the
  defaults of SETTLE-R12, SETTLE-R13 and SETTLE-R16.

### Option 2: settlement plus payments

Move `/settlement/*` and `/payment/*` together, taking `SETTLEMENT` and
`PAYMENT` with them so the foreign key and the `PaymentIssueAction` read of
`SettlementDAO` stay inside one service.

- For: removes the cross-service `SETTLEMENT` read and the shared foreign key
  from Option 1; matches how `transcripts/index.json` currently groups
  `payment_issue` and `payment_history` under module `settlement`.
- Against: there is no payment specification equivalent to SPEC-SETTLE-001,
  so the payment side of the boundary would be moved on the strength of two
  transcripts and unread source. Payment issue depends on the payment
  history screen for its `db_state` probes, on `nextId("PAYMENT")`, on
  `CHK-<id>` numbering and on a fixed issue date, none of which has been
  reviewed for accidental versus intended behaviour. It doubles the surface
  to reproduce before anything can be proven, and it delays the first proof
  point by the time needed to write and review a payment spec.

### Option 3: whole claims domain

Move intake, workbench, settlement and payment (everything that touches
`CLAIM`) in one step, leaving policy and reporting behind.

- For: `CLAIM` would have a single owner; no shared schema between old and
  new.
- Against: only 6 of the 22 transcripts have a behavioural spec. Reporting
  reads `CLAIM`, `SETTLEMENT` and `PAYMENT` aggregates through `ReportDAO`
  and would either stay coupled to the moved tables or need its own
  extraction. Workbench and intake carry the lenient date parsing and
  validation behaviours captured by `intake_lenient_date`,
  `intake_bad_date`, `intake_missing_claimant` and
  `intake_missing_description`, which have not been specified. This is a
  rewrite, not an extraction, and offers no intermediate point at which
  equivalence can be demonstrated.

## Decision

Adopt Option 1. The settlement module, and only the settlement module, is
extracted from the Struts application into a Spring Boot service:

- Location: `services/settlement-service` in this repository, with its own
  `pom.xml` and `Makefile`.
- Listening port: 8083. The legacy application keeps port 8080 and the
  `/claims` context.
- Routes: the service serves `/claims/settlement/calculate.do`,
  `/claims/settlement/save.do` and `/claims/settlement/detail.do` with the
  same request parameters (SETTLE-R01 to SETTLE-R03), the same HTTP 200 and
  view-marker results, the same field spans (SETTLE-R07, SETTLE-R08,
  SETTLE-R34, SETTLE-R35) and the same error-page forward for an unparseable
  deductible (SETTLE-R18).
- Calculation: `SettlementCalculator` is ported unchanged in semantics,
  including binary double arithmetic and `Math.round` (SETTLE-R21 to
  SETTLE-R33). No rounding, cap or default is "fixed" during the move; every
  open question in SPEC-SETTLE-001 section 5 remains open and is answered
  after the replay is green, not during it.
- Data: during the transition the service and the legacy application share
  the existing HSQLDB schema. The service owns writes to `SETTLEMENT`
  (SETTLE-R38, SETTLE-R39, SETTLE-R42); the legacy application keeps
  read-only access to `SETTLEMENT` for `PaymentIssueAction` and the
  `fk_payment_settlement` constraint. The service reads `CLAIM` and `POLICY`
  for the policy limit (SETTLE-R14, SETTLE-R49) and does not write them
  (SETTLE-R44, SETTLE-R51). Because file-mode HSQLDB is single-process, the
  database is run in server mode for the transition and both applications
  connect over JDBC to it. Every legacy JDBC entry point must follow: not
  only `ConnectionPool` (which already prefers a configured data source over
  the file path) but also `ClaimsActionSupport.openConnection`, which
  `nextId`, `findClaim` and the other action helpers use to open
  `jdbc:hsqldb:file:` directly and which would fail once the server owns the
  catalog files. The server binds to `127.0.0.1` only, the `SA` password is
  replaced by a credential read from the environment, and this topology is
  for local development and CI; the deployed topology is decided in a later
  ADR before the service leaves the transition.
- Identity: the service never takes the operator from a request parameter.
  The legacy application forwards the session `user` to the service in a
  signed header (`X-NorthStar-User` plus an HMAC over user and timestamp
  with a shared secret from the environment); the service rejects requests
  without a valid signature with HTTP 401 and listens on `127.0.0.1` during
  the transition, so only the legacy proxy and the replay tool (which signs
  with the transcript's `actor`) can reach it. `calculated_by` and `savedBy`
  therefore still show `supervisor` in the transcripts (SETTLE-R20,
  SETTLE-R40).
- Not moved: `SettlementService.calculateAndSave`, which is dead code with
  conflicting defaults (SETTLE-R47, OQ-13), is not ported.
- Stays in Struts for now: payments, workbench, reporting, policy and intake.

Acceptance: the six settlement transcripts replay green against the service
and the three supplementary checks in Verification (persistence, detail
route, SETTLE-R18 error page) pass. Nothing else is required to call the
extraction done.

## Consequences

1. **Shared schema during the transition.** `SETTLEMENT` is written by the
   service and read by the legacy payment module; `CLAIM` and `POLICY` are
   written by the legacy application and read by the service. This is a
   deliberate interim state. Splitting the database is a later ADR and
   depends on answering OQ-10 (settlement history) and on giving payments a
   contract of their own.
2. **Id allocation moves with the module.** `max(settlement_id) + 1`
   (SETTLE-R39) is computed only by the service once the legacy
   `/settlement/save` route is retired; until then the two allocators must
   not run concurrently or they can collide. SETTLE-R45 (detail shows the
   highest id) continues to hold only while ids are allocated in increasing
   order.
3. **Accidental behaviour is preserved on purpose.** Defaults for claim 119,
   covered amount 5000 and limit 10000 (SETTLE-R11, SETTLE-R12, SETTLE-R49;
   OQ-01 to OQ-03), the HTTP 200 error page for `deductible=abc`
   (SETTLE-R18; OQ-04), the double rounding (SETTLE-R28, SETTLE-R29; OQ-05),
   the fixed date `2019-04-01` (SETTLE-R41; OQ-09) and the parameterless save
   link (SETTLE-R10; OQ-08) are all reproduced. The service is the place
   where those decisions will later be implemented, but the replay oracle
   only stays valid if they are not implemented yet. The first such decision
   already exists: CHG-001 (`docs/changes/CHG-001-invalid-deductible.md`)
   closes OQ-04 and is recorded as SETTLE-R18 v2 (redisplay the calculate
   screen with `settlement.deductible.invalid`, HTTP 200, no settlement
   computed). It is implemented in the service only after step 9 below is
   green, at which point the `r18-error` check is replaced by a check for
   SETTLE-R18 v2 and a transcript for it is captured; until then the service
   reproduces SETTLE-R18 exactly.
4. **`transcripts/index.json` files `payment_issue` and `payment_history`
   under module `settlement`.** The index is generated by
   `tools/capture/capture.py`, whose `SCENARIOS` table tags both payment
   scenarios with module `settlement` even though their requests go to
   `/claims/payment/issue.do` and `/claims/payment/history.do` and their
   expected forwards are `/WEB-INF/jsp/payment/issue.jsp` and
   `/WEB-INF/jsp/payment/history.jsp`. Payments stay in Struts under this
   decision, so a replay that selects scenarios by `module == "settlement"`
   picks up eight scenarios, not six, and sends the two payment requests to
   a service that has no `/payment/*` routes. What that replay will show for
   those two scenarios: the service returns an HTTP 404 (no route), the
   harness's `result()` finds no `ns:view` marker and therefore reports
   `forward:/WEB-INF/jsp/login.jsp`, `business_fields` is empty, and the
   `payment.count.claim.119`, `payment.61.amount` and `payment.61.status`
   probes of `payment_issue` resolve to `0` or `""`; each field differs from
   the transcript, so both scenarios fail, and the failures are a
   classification error in the index, not a settlement regression. The
   replay tool therefore selects settlement scenarios by request path prefix
   `/claims/settlement/` and reports the two payment scenarios as
   `out-of-boundary (module=settlement, path=/claims/payment/...)` rather
   than as passes or as settlement failures. Re-filing them under module
   `payment` in `capture.py` (which regenerates `index.json`) is step 8 of
   the migration plan and is the only change to `transcripts/` this
   decision requires.
5. **Two processes are needed to replay.** Login (`/login.do`), the
   `claim.120.status` probe (`/workbench/view.do`) and the seed all live in
   the legacy application, while the six settlement requests and the
   `settlement.claim.119.amount` probe go to the service. The replay tool
   routes by path prefix: `/claims/settlement/*` to `localhost:8083`,
   everything else to `localhost:8080`, sharing one cookie jar.
6. **The legacy settlement code stays until the switch-over step.** The
   Struts `/settlement/*` mappings are first shadowed by a transparent
   reverse proxy inside the legacy WAR (Jetty's `ProxyServlet.Transparent`
   from `jetty-proxy`, mapped to `/settlement/*` in `web.xml` behind
   `AuthFilter`, forwarding method, query string, body and cookies to
   `127.0.0.1:8083` and adding the signed identity header) and only removed
   after `make capture` through the legacy front door is stable with the
   proxy on, so `make test` and `make capture` keep passing at every step.
7. **CI grows a second job.** The existing job (Maven verify, `make
   capture`, `git diff --exit-code transcripts/`) is unchanged; a new job
   builds, lints, tests and replays the service. Replay needs HSQLDB, the
   legacy application and the service up together, so the job starts each
   in the background, waits on an explicit readiness check for each (JDBC
   connect; `GET /claims/login.do` containing `Claims Login`, the same
   check `capture.py` uses; `GET /claims/actuator/health` returning 200)
   and tears all three down in an `if: always()` step.
8. **Transcripts alone do not prove persistence or the detail route.** The
   `settlement.claim.119.amount` probe reads the calculate endpoint, not the
   saved row (OQ-12), and no transcript calls `/settlement/detail.do`. A
   service that rendered correctly but never inserted would pass the six
   replays and step 10 would then retire the only working writer while
   `PaymentIssueAction` still reads `SETTLEMENT`. The replay therefore
   carries the supplementary checks listed in Verification, and they are
   part of the acceptance gate.
9. **What is not decided here.** The ownership of `CLAIM`, the future of
   payments, the answers to OQ-01 to OQ-14, the deployed database and
   network topology after the transition, and any change to the HSQLDB
   engine beyond running it in server mode for the transition.

## Verification

The extraction is verified by replaying the six settlement transcripts against
the service and comparing every field the harness records: `status`, `result`,
`business_fields`, `validation_errors` and `db_state`
(`transcripts/README.md`).

| Transcript                      | What it proves in the service                                                                                   | Rules              |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------|--------------------|
| `settlement_calculate`          | Route, view marker, five field spans, cap at the claim's policy limit with `cappedAtLimit: true`                  | R01, R07, R14, R22, R24, R34, R35 |
| `settlement_save`               | Save route and marker, two field spans, recompute on save, `savedBy: supervisor`, probe reads `1000.00`          | R02, R08, R20, R37, R43 |
| `settlement_blank_deductible`   | Empty deductible is 0.00, gross is covered minus depreciation, `CLAIM` untouched (`claim.120.status = CLOSED`)   | R16, R21, R26, R44 |
| `settlement_half_cent`          | 1.005 displays as 1.01 but settles to 1.00 (double `Math.round`), inputs echoed unrounded                       | R28, R29, R31      |
| `settlement_policy_cap`         | 19900 after deductible is capped to 1000.00                                                                     | R24, R30           |
| `settlement_deductible_floor`   | Deductible above the loss floors to 0.00 and `deductibleApplied` still reports 2000.00                          | R23, R30, R33      |

The `settlement.claim.119.amount` probe in `settlement_save` is a GET to
`/settlement/calculate.do?claimId=119` with no other parameters, so a green
replay also proves the defaults of SETTLE-R12, SETTLE-R13 and SETTLE-R16 and
GET handling on the calculate route.

Supplementary checks run by the replay tool after the six transcripts, and
required for acceptance:

| Check         | What it does                                                                                                                                                                                                                                                                                              | Rules              |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|
| `persistence` | Reads `count(*)` and `max(settlement_id)` from `SETTLEMENT` over JDBC (server mode, so this is allowed) before and after `settlement_save`; requires count +1, id = previous max + 1, and the new row's `settlement_amount = 1000.00`, `calculated_by = 'supervisor'`, `calculated_date = 2019-04-01`      | R38, R39, R40, R41, R42 |
| `detail`      | `GET /claims/settlement/detail.do?claimId=119` after the save; requires forward `/WEB-INF/jsp/settlement/detail.jsp`, `detailSettlementId` equal to the id from `persistence`, `detailAmount = 1000.00`, `detailCapped = true`, `detailDate = 2019-04-01`, and `detailClaimId = 119`                      | R03, R34, R45      |
| `r18-error`   | `POST /claims/settlement/calculate.do` `claimId=120&coveredAmount=1000&deductible=abc&depreciation=0`; requires HTTP 200 and forward `/WEB-INF/jsp/error.jsp` (the execution evidence of SETTLE-R18, which no transcript covers)                                                                      | R18                |

In addition:

- `Inferred`-only rules that the transcripts and checks cannot reach
  (SETTLE-R15, SETTLE-R17, SETTLE-R25, SETTLE-R27, SETTLE-R46, SETTLE-R48) are
  covered by unit tests in the service, each named after its rule id, as is
  the identity rule (a request without a valid signed identity header is
  rejected with HTTP 401).
- The legacy oracle itself must stay green throughout: `make test` and
  `make capture && git diff --exit-code transcripts/` pass at every step.
- Replay is run in both directions before switch-over: against the legacy
  application (must pass, proving the tool) and against the service (must
  pass, proving the extraction).

The replay is exposed as `make -C services/settlement-service replay`. Its
output lists every selected scenario and supplementary check with `pass`,
`fail` (with the first differing field) or `out-of-boundary`, and exits
non-zero on any `fail`.

## Migration plan

Each step names the command that proves it. Legacy-side proofs use the root
`Makefile` targets `make build`, `make test` and `make capture`. Service-side
proofs use the targets `services/settlement-service/Makefile` adds: `build`,
`test`, `lint`, `start` and `replay`, invoked as
`make -C services/settlement-service <target>`. A step is complete only when
its command exits 0 on this branch.

1. **Baseline the legacy application on this branch.**
   Proof: `make build && make test && make capture && git diff --exit-code transcripts/`
   (the local equivalent of the CI job, which runs `mvn -B verify`,
   `make capture` and the transcript diff; confirms the oracle is stable
   before anything moves).

2. **Land the contract.** Merge SPEC-SETTLE-001 and this ADR (pull request
   #1) with a merge commit, not a squash, so the commit hashes in
   `docs/TRACEABILITY.md` stay reachable (see step 12). No `src/` or
   `transcripts/` change is part of this step.
   Proof: `git diff --stat origin/main...HEAD -- src transcripts` prints
   nothing, `make test` still passes, and after the merge
   `python3 tools/traceability.py --check` exits 0 on `main`.

3. **Scaffold the service.** Create `services/settlement-service` (Spring
   Boot, `server.port=8083`, context path `/claims`) with a `Makefile`
   defining `build` (`mvn -B clean package`), `test` (`mvn -B verify`),
   `lint` (`mvn -B checkstyle:check`), `start` (run the packaged jar on
   8083 and wait for readiness) and `replay` (`python3
   tools/replay/replay.py`, see step 7). Add the actuator health endpoint
   (`/claims/actuator/health`) only.
   Proof: `make -C services/settlement-service build && make -C services/settlement-service lint`.

4. **Port the calculator with rule-named tests.** Copy the semantics of
   `SettlementCalculator` (SETTLE-R21 to SETTLE-R33) and write one unit test
   per rule, including the six transcript input sets and the `Inferred`
   edge cases (R25 exact-limit, R27 zero limit, R17 whitespace deductible).
   Proof: `make -C services/settlement-service test`.

5. **Implement the three routes, the field-span rendering and the identity
   check.** Serve `/claims/settlement/calculate.do`, `save.do` and
   `detail.do` with the `ns:view` marker, `f_NAME` spans, two-decimal money
   formatting (SETTLE-R34), the defaults of SETTLE-R11 to SETTLE-R13 and
   SETTLE-R49, the HTTP 200 error page of SETTLE-R18, the fixed date of
   SETTLE-R41, and the signed `X-NorthStar-User` header (401 without it).
   Proof: `make -C services/settlement-service start` (the target blocks
   until `GET http://127.0.0.1:8083/claims/actuator/health` returns 200)
   and, in a second shell, a signed
   `curl 'http://127.0.0.1:8083/claims/settlement/calculate.do?claimId=119'`
   prints `200` while the same request without the header prints `401`;
   `make -C services/settlement-service test` passes with the route and
   identity tests added.

6. **Move every legacy JDBC entry point to configurable connection
   settings, then share the database in server mode.** First make
   `ConnectionPool` and `ClaimsActionSupport.openConnection` (used by
   `nextId`, `findClaim` and the other action helpers) read the same three
   properties `claims.db.url`, `claims.db.user` and `claims.db.password`,
   defaulting to today's `jdbc:hsqldb:file:` URL, `SA` and the empty
   password, so behaviour is unchanged when they are unset; both today pass
   `SA` and `""` literally, so a URL-only property would leave `nextId` and
   `findClaim` unable to authenticate once the server password changes. The
   `jdbc/ClaimsDB` data source and the service read the same three values.
   The two file-mode utilities are handled explicitly and are not migrated:
   `DatabaseBootstrap` (`make seed`) keeps opening the catalog directly as
   `SA` and is run before the server is started, as today (`docs/RUNBOOK.txt`
   already requires the application to be stopped for a seed), and the
   server is started with the same non-default credential that the seed
   creates; `DatabaseDump` likewise runs only while the server is stopped.
   Then start HSQLDB as a server bound to `127.0.0.1` on the seeded
   `target/db/northstar` catalog with that credential from the environment,
   and point the service and the legacy application at it. The service owns
   writes to `SETTLEMENT`; the legacy application keeps its reads.
   Proof: with the properties unset, `make test && make capture && git diff --exit-code transcripts/`
   (no behaviour change); then with the server running under the non-default
   credential and `claims.db.url`, `claims.db.user` and `claims.db.password`
   pointing at it, `make capture && git diff --exit-code transcripts/`
   again, which exercises a legacy `settlement_save` (and therefore
   `nextId` and `findClaim`) through the server with those credentials, and
   `make -C services/settlement-service start` connects to the same catalog.

7. **Add the replay tool and replay against both sides.** Add
   `tools/replay/replay.py`, reusing the harness's `request`, `fields`,
   `result` and `probe` functions from `tools/capture/capture.py`, selecting
   scenarios whose `request.path` starts with `/claims/settlement/`, routing
   those to `127.0.0.1:8083` with the signed identity header and all other
   requests (login, workbench probe) to `localhost:8080` with a shared
   cookie jar, reporting `module == "settlement"` scenarios outside that
   prefix as `out-of-boundary`, and running the `persistence`, `detail` and
   `r18-error` checks from Verification after the six scenarios.
   Proof: `make -C services/settlement-service replay` prints six `pass`
   lines for the transcripts, three `pass` lines for the checks, two
   `out-of-boundary` lines for `payment_issue` and `payment_history`, and
   exits 0; run first with
   `REPLAY_SETTLEMENT_BASE=http://localhost:8080/claims` (legacy, proves the
   tool) and then with the default `http://127.0.0.1:8083/claims` (service,
   proves the extraction).

8. **Re-file the two payment scenarios.** Change the module of
   `payment_issue` and `payment_history` from `settlement` to `payment` in
   the `SCENARIOS` table of `tools/capture/capture.py` and regenerate
   `transcripts/index.json`.
   Proof: `make capture && git diff --stat transcripts/` lists only
   `transcripts/index.json`, and `make -C services/settlement-service replay`
   no longer reports any `out-of-boundary` scenario.

9. **Shadow the legacy routes through a transparent proxy.** Add
   `jetty-proxy` to the legacy WAR and map `ProxyServlet.Transparent` to
   `/settlement/*` in `web.xml`, behind `AuthFilter` and ahead of the Struts
   `ActionServlet`, enabled only when the system property
   `settlement.proxy.target` (for example `http://127.0.0.1:8083/claims`) is
   set. The proxy preserves method, query string, body, `Cookie` and
   `Set-Cookie`, rewrites only the host and port, and adds the signed
   `X-NorthStar-User` header from the session `user`. With the property
   unset the legacy actions still answer, so the switch is reversible.
   Proof: with the property set, `make capture && git diff --exit-code transcripts/`
   is empty (the six transcripts are byte-identical through the legacy front
   door, now served by the service) and `make -C services/settlement-service replay`
   passes; with the property unset the same two commands still pass.

10. **Retire the legacy settlement code.** Remove `SettlementCalculateAction`,
    `SettlementSaveAction`, `SettlementDetailAction`, `SettlementForm`, the
    settlement JSPs and the dead `SettlementService` (SETTLE-R47, OQ-13);
    keep the `SettlementDAO.findByClaim` read path for `PaymentIssueAction`.
    This step is allowed only after step 7's `persistence` and `detail`
    checks pass against the service, since they, not the six transcripts,
    prove the service is writing the rows payments will read.
    Proof: `make build && make test && make capture && git diff --exit-code transcripts/`
    and `make -C services/settlement-service replay` all pass.

11. **Extend CI.** Add a `settlement-service` job to
    `.github/workflows/` that runs `make -C services/settlement-service build`,
    `lint` and `test`, then `make seed`, starts HSQLDB in server mode, `make
    run` and `make -C services/settlement-service start` in the background,
    waits on the three readiness checks (JDBC connect to the server;
    `GET /claims/login.do` containing `Claims Login`; `GET
    /claims/actuator/health` returning 200, each with a timeout), runs `make
    -C services/settlement-service replay`, and stops all three processes in
    an `if: always()` step so a failed replay cannot leave the runner
    occupied. The existing `verify` job is unchanged.
    Proof: both jobs are green on the pull request (`git_pr_checks` or the
    GitHub checks tab), and locally
    `make -C services/settlement-service build lint test` exits 0.

12. **Merge.** Merge the service branch into `main` with a merge commit
    (not a squash) once steps 1-11 are green. The `commit` column of
    `docs/TRACEABILITY.md` records the hashes of the commits reachable from
    `HEAD` whose messages name a rule; a squash replaces them with one new
    commit, so the committed matrix would no longer match its generator. The
    same applies to pull request #1 (step 2). If a branch is squashed
    anyway, the first commit on `main` afterwards regenerates the matrix.
    Proof: on `main`, `make test && make -C services/settlement-service test && make -C services/settlement-service replay`
    exit 0, `git log --oneline -1 origin/main` shows the merge commit, and
    `python3 tools/traceability.py --check` exits 0.

After step 12 the settlement service is the system of record for settlement
behaviour and SPEC-SETTLE-001 moves to its next version to start closing
OQ-01 to OQ-14 against the service rather than against the monolith.

## As implemented

Recorded 2026-09-20 against pull request #2 (`Extract settlement module into
services/settlement-service`). The decision above (Option 1: settlement alone,
port 8083, calculator ported unchanged, payments stay in Struts) stands; the
stack and the build targets the pull request ships differ from the plan in the
following ways. Each difference is recorded here so the ADR is not stale when
the pull request merges; none of them changes a SETTLE-R rule.

| Planned                                                                                            | Implemented                                                                                                                                                                                                                                                                                             | Reason                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Shared HSQLDB catalog, run in server mode, both applications connected over JDBC                   | PostgreSQL 16 in Docker, a schema owned by the service (`settlement`, `claim`, `policy`) managed by Flyway (`V1__settlement_schema.sql`, `V2__settlement_seed.sql`), seeded with the policy 9001/9002, claim 119/120 and settlement 119/120 rows from `DatabaseBootstrap` that the six transcripts need. | The extraction brief fixed "its own PostgreSQL schema managed by Flyway". Server-mode HSQLDB would also have required changing `ClaimsActionSupport.openConnection` and `ConnectionPool` under `src/`, which the extraction is forbidden to touch; the shared-catalog transition topology therefore needs a legacy-side change and is deferred to the deployment ADR. The seed is a transcript fixture, not the production data set (OQ-12 stays open). |
| `make -C services/settlement-service build \| lint \| test \| start \| replay`                     | Root `docker-compose.yml` (variables `NS`, `PORT_OFFSET`, `MAVEN_MIRROR`, `DB_PASSWORD`) and root Makefile targets `up`, `down`, `parity`, `service-build`, `service-test`, `service-lint`, `sast`. The service has no Makefile of its own; the Maven build is driven from the root targets.             | The brief asked for root targets and a namespaced Compose stack so that a second extraction (`NS=<other> PORT_OFFSET=<n>`) can run beside the first; `make -C` targets would not have given the stack a namespace. The existing root targets `build`, `run`, `seed`, `test`, `capture` and `clean` are unchanged.                                                                                                                                  |
| The service serves `/claims/settlement/*.do` directly                                              | The service serves `POST /api/settlement/calculate` (also `GET`, for the probe), `POST /api/settlement/save` and `GET /api/settlement/detail`, returning the JSON `ScreenResponse` envelope (`legacyForward`, `fields`, `validationErrors`); `parity/routes.yaml` maps each legacy `.do` path to its service route. | The monolith is not modified or proxied in this pull request, so nothing routes `.do` traffic to the service yet; the declarative route file carries the mapping until the front door is decided. Forward names, field names and formatting are preserved (SETTLE-R01, R02, R07, R08, R34, R35).                                                                                                                                                  |
| Signed `X-NorthStar-User` identity header, HTTP 401 without it, service bound to `127.0.0.1`       | No authentication on the service; the operator is the `settlement.operator` property (Compose sets `supervisor`) and is echoed as `savedBy` / stored as `calculated_by` (SETTLE-R20, R40). The Compose stack publishes the service and PostgreSQL on the host ports derived from `PORT_OFFSET`.          | The signing side lives in the monolith (`AuthFilter`, session `user`), which is out of scope for this change. Authentication is raised as an open review item on the pull request and is decided together with the deployment topology, not in the extraction.                                                                                                                                                                                       |
| Replay tool in the service directory with verdicts `pass` / `fail` / `out-of-boundary` and three supplementary checks (`persistence`, `detail`, `r18-error`) | `parity/replay.py` (Python 3, PyYAML only) with `parity/routes.yaml`; verdicts `PASS` / `FAIL` / `SKIP (not yet extracted)` / `SKIP (route not extracted)`; writes `parity/report.md` and `parity/report.json` (the latter read by `tools/traceability.py`). The supplementary checks are not part of the replay: R38-R42 are covered by `SettlementServiceTest.settleR38R39SaveAllocatesNextIdAndInserts`, `settleR37R42SaveRecomputesAndStoresCalculatorOutput`, `settleR40R48CalculatedByIsOperator`, `settleR41CalculatedDateIsFixed`; R03/R45 by `settleR03R45DetailScreen`; R18 by `settleR18NonNumericDeductibleIsErrorScreenWith200`. | The brief fixed the harness location, its dependency set and the `SKIP` vocabulary (it must skip modules that `transcripts/index.json` marks as not extracted, and report the two payment scenarios filed under `settlement`). A JDBC-level persistence check against PostgreSQL would need the harness to carry a database driver, which the PyYAML-only constraint excludes. Bidirectional replay against the legacy application was not run. |
| Detail for a claim with no settlement fails in `detail.jsp` (SETTLE-R46)                          | `GET /api/settlement/detail` returns HTTP 404 with an empty body.                                                                                                                                                                                                                                        | The legacy outcome is a JSP crash with no recorded transcript; a defined status was chosen and is listed as a deliberate change in the pull request and in `docs/KNOWN_LEGACY_QUIRKS.md`, not silently altered.                                                                                                                                                                                                                                   |
| CI job starts HSQLDB in server mode, `make run` and the service, then replays                     | CI job `settlement-service` runs `make service-build`, `service-test`, `service-lint`, `sast`, `make up`, `make parity`, checks `git diff --exit-code transcripts/`, uploads `parity/report.md`, and runs `make down` in an `if: always()` step. The legacy application is not started.                | The service does not share a database with the monolith, so the monolith is not needed to replay against the service. The existing `verify` job is unchanged, so the legacy oracle is still built and tested on every push.                                                                                                                                                                                                                       |

Unchanged from the decision: location `services/settlement-service`, port
8083 inside the container, Spring Boot 3 on Java 21 with Maven, calculator
semantics (binary double arithmetic and `Math.round`, SETTLE-R21 to R33),
`SettlementService.calculateAndSave` not ported (SETTLE-R47), parameterised
SQL as the only sanctioned change to legacy code, and the six-transcript
acceptance gate: `make up NS=settlement PORT_OFFSET=100 && make parity
NS=settlement PORT_OFFSET=100 MODULE=settlement` must print `6 PASS, 0 FAIL`.
Steps 5 (HSQLDB server mode), 6 (identity header) and 11 (CI starting the
legacy stack) of the migration plan are therefore superseded by this section;
the remaining steps apply as written with `make service-*` / `make parity` in
place of `make -C services/settlement-service <target>`.
