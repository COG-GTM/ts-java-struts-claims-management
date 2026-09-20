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
  connect over JDBC to it (the legacy `ConnectionPool` already prefers a
  configured data source over the file path).
- Identity: the service accepts the operator identity established by the
  legacy login, so `calculated_by` and `savedBy` still show `supervisor`
  in the transcripts (SETTLE-R20, SETTLE-R40).
- Not moved: `SettlementService.calculateAndSave`, which is dead code with
  conflicting defaults (SETTLE-R47, OQ-13), is not ported.
- Stays in Struts for now: payments, workbench, reporting, policy and intake.

Acceptance: the six settlement transcripts replay green against the service
(see Verification). Nothing else is required to call the extraction done.

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
   only stays valid if they are not implemented yet.
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
   Struts `/settlement/*` mappings are first shadowed by a forward to port
   8083 and only removed after `make capture` through the legacy front door
   is stable with the forward in place, so `make test` and `make capture`
   keep passing at every step.
7. **CI grows a second job.** The existing job (Maven verify, `make
   capture`, `git diff --exit-code transcripts/`) is unchanged; a new job
   builds, lints, tests and replays the service.
8. **What is not decided here.** The ownership of `CLAIM`, the future of
   payments, the answers to OQ-01 to OQ-14, and any change to the HSQLDB
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

In addition:

- The `settlement.claim.119.amount` probe in `settlement_save` is a GET to
  `/settlement/calculate.do?claimId=119` with no other parameters, so a green
  replay also proves the defaults of SETTLE-R12, SETTLE-R13 and SETTLE-R16 and
  GET handling on the calculate route.
- The execution evidence of SETTLE-R18 (`POST /claims/settlement/calculate.do`
  `claimId=120&coveredAmount=1000&deductible=abc&depreciation=0` gives HTTP 200
  and forward `/WEB-INF/jsp/error.jsp`) is added to the service's test suite
  as a seventh replay case, because no transcript covers it.
- `Inferred`-only rules that the transcripts cannot reach (SETTLE-R15,
  SETTLE-R17, SETTLE-R25, SETTLE-R27, SETTLE-R41, SETTLE-R46, SETTLE-R48) are
  covered by unit tests in the service, each named after its rule id.
- The legacy oracle itself must stay green throughout: `make test` and
  `make capture && git diff --exit-code transcripts/` pass at every step.
- Replay is run in both directions before switch-over: against the legacy
  application (must pass, proving the tool) and against the service (must
  pass, proving the extraction).

The replay is exposed as `make -C services/settlement-service replay`. Its
output lists every selected scenario with `pass`, `fail` (with the first
differing field) or `out-of-boundary`, and exits non-zero on any `fail`.

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

2. **Land the contract.** Merge SPEC-SETTLE-001 v0.2 and this ADR
   (pull request #1). No `src/` or `transcripts/` change is part of this step.
   Proof: `git diff --stat origin/main...HEAD -- src transcripts` prints
   nothing, and `make test` still passes.

3. **Scaffold the service.** Create `services/settlement-service` (Spring
   Boot, `server.port=8083`, context path `/claims`) with a `Makefile`
   defining `build` (`mvn -B clean package`), `test` (`mvn -B verify`),
   `lint` (`mvn -B checkstyle:check`), `start` (run the packaged jar on
   8083) and `replay` (`python3 tools/replay/replay.py`, see step 7). Add a
   health endpoint only.
   Proof: `make -C services/settlement-service build && make -C services/settlement-service lint`.

4. **Port the calculator with rule-named tests.** Copy the semantics of
   `SettlementCalculator` (SETTLE-R21 to SETTLE-R33) and write one unit test
   per rule, including the six transcript input sets and the `Inferred`
   edge cases (R25 exact-limit, R27 zero limit, R17 whitespace deductible).
   Proof: `make -C services/settlement-service test`.

5. **Implement the three routes and the field-span rendering.** Serve
   `/claims/settlement/calculate.do`, `save.do` and `detail.do` with the
   `ns:view` marker, `f_NAME` spans, two-decimal money formatting
   (SETTLE-R34), the defaults of SETTLE-R11 to SETTLE-R13 and SETTLE-R49, the
   HTTP 200 error page of SETTLE-R18, and the fixed date of SETTLE-R41.
   Proof: `make -C services/settlement-service start` and, in a second
   shell, `curl -s -o /dev/null -w '%{http_code}\n' 'http://localhost:8083/claims/settlement/calculate.do?claimId=119'`
   prints `200`; `make -C services/settlement-service test` passes with the
   route tests added.

6. **Share the database in server mode.** Start HSQLDB as a server on the
   seeded `target/db/northstar` catalog, point the service at it, and point
   the legacy `ConnectionPool` at the same server URL through its configured
   data source. The service owns writes to `SETTLEMENT`; the legacy
   application keeps its reads.
   Proof: `make seed && make run` with the server-mode data source, then
   `make capture && git diff --exit-code transcripts/` (the legacy oracle is
   unchanged by the connection change), and
   `make -C services/settlement-service start` connects to the same catalog.

7. **Add the replay tool and replay against both sides.** Add
   `tools/replay/replay.py`, reusing the harness's `request`, `fields`,
   `result` and `probe` functions from `tools/capture/capture.py`, selecting
   scenarios whose `request.path` starts with `/claims/settlement/`, routing
   those to `localhost:8083` and all other requests (login, workbench probe)
   to `localhost:8080` with a shared cookie jar, and reporting `module ==
   "settlement"` scenarios outside that prefix as `out-of-boundary`.
   Proof: `make -C services/settlement-service replay` prints six `pass`
   lines, two `out-of-boundary` lines for `payment_issue` and
   `payment_history`, and exits 0; run first with
   `REPLAY_SETTLEMENT_BASE=http://localhost:8080/claims` (legacy, proves the
   tool) and then with the default `http://localhost:8083/claims` (service,
   proves the extraction).

8. **Re-file the two payment scenarios.** Change the module of
   `payment_issue` and `payment_history` from `settlement` to `payment` in
   the `SCENARIOS` table of `tools/capture/capture.py` and regenerate
   `transcripts/index.json`.
   Proof: `make capture && git diff --stat transcripts/` lists only
   `transcripts/index.json`, and `make -C services/settlement-service replay`
   no longer reports any `out-of-boundary` scenario.

9. **Shadow the legacy routes.** Make the Struts `/settlement/*` mappings
   forward to the service on 8083 (strangler style) while leaving the legacy
   actions in place behind a switch.
   Proof: with the forward enabled, `make capture && git diff --exit-code transcripts/`
   is empty (the six transcripts are byte-identical through the legacy front
   door) and `make -C services/settlement-service replay` passes.

10. **Retire the legacy settlement code.** Remove `SettlementCalculateAction`,
    `SettlementSaveAction`, `SettlementDetailAction`, `SettlementForm`, the
    settlement JSPs and the dead `SettlementService` (SETTLE-R47, OQ-13);
    keep `SettlementDAO.findByClaim` read path for `PaymentIssueAction`.
    Proof: `make build && make test && make capture && git diff --exit-code transcripts/`
    and `make -C services/settlement-service replay` all pass.

11. **Extend CI.** Add a `settlement-service` job to
    `.github/workflows/` running `make -C services/settlement-service build`,
    `lint`, `test`, `start` (in the background) and `replay` after the
    existing legacy verification job.
    Proof: both jobs are green on the pull request (`git_pr_checks` or the
    GitHub checks tab), and locally
    `make -C services/settlement-service build lint test` exits 0.

12. **Merge.** Squash or merge the service branch into `main` once steps
    1-11 are green.
    Proof: on `main`, `make test && make -C services/settlement-service test && make -C services/settlement-service replay`
    exit 0, and `git log --oneline -1 origin/main` shows the merge commit.

After step 12 the settlement service is the system of record for settlement
behaviour and SPEC-SETTLE-001 moves to its next version to start closing
OQ-01 to OQ-14 against the service rather than against the monolith.
