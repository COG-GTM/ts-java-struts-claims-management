# Area: tests (JUnit 5 suite)

Test-only stream. No controllers, templates, Wave 0 code, parity tools, snapshots or
transcripts are changed. Everything lives in `src/test/java/com/northstar/claims/tests/`
plus a surefire configuration and an `areas` profile in `modernized/pom.xml`.

## Running

| Command | What runs |
| --- | --- |
| `make -C modernized test` (`mvn -B test`) | Everything except `@Tag("area")`: Wave 0 tests, unit tests, seeded repository tests, foundation (login) transcript. Must be green. |
| `mvn -Pareas test` (from `modernized/`, Java 21) | Only the `@Tag("area")` transcript replays (21 tests). Red until the area PRs merge. |

## Layout

- `tests/unit/` — no Spring context.
  - `SettlementCalculatorQuirksTest`, `ClaimsSupportHelpersTest`, `NsFieldFormatterTest`, `CredentialRuleTest`.
- `tests/repository/SeededRepositoryIntegrationTest` — every public method of the 7 DAOs,
  `ClaimManager`, `PolicyManager`, `SettlementService` and the `ClaimsSupport` database helpers.
  Uses a scratch copy of the seeded HSQLDB file database (snapshot `01_login`, which is the
  fixed seed because login does not write), restored before each test.
- `tests/support/SnapshotDatabaseTest` — shared `@SpringBootTest(RANDOM_PORT)` base. It
  points `claims.db.path` at `target/test-snapshot-db/northstar` and swaps the database files
  for a committed snapshot: `SHUTDOWN`, delete `northstar.*`, copy the snapshot files (same
  filter as `parity/common.py`, i.e. skip `.lck`). The next connection reopens the files, so
  one Spring context serves every scenario. It uses `SimpleDriverDataSource` in tests so no
  pool can keep the old files open. Production keeps HikariCP.
- `tests/transcript/` — `TranscriptClient` is a Java port of the `capture.py` request,
  `fields()`, `result()`, `ERROR_RE` and `probe()` logic, with the same regexes and fallbacks.
  `TranscriptReplayTest.replay(scenario)` restores `parity/snapshots/NN_<scenario>`, opens a
  cookie session (logs in as supervisor unless the scenario is `login`, like `common.opener`),
  sends `request` from `transcripts/<scenario>.json`, re-reads each `db_state` key in the same
  session, and checks `status`, `result`, `business_fields`, `validation_errors` and `db_state`
  against `expected`.
  - One class per area: `FoundationTranscriptTest` (untagged), `PolicyTranscriptTest`,
    `IntakeTranscriptTest`, `WorkbenchTranscriptTest`, `SettlementTranscriptTest`,
    `PaymentTranscriptTest`, `ReportsTranscriptTest`, all tagged `area`. Together they cover
    all 22 transcripts.

## Status at PR time

- Default run: 48 tests, 0 failures (includes `FoundationTranscriptTest` 1/1).
- `-Pareas` on the base branch: 21 run, 21 fail, 0 pass. This is expected because the area
  controllers are not on the base yet.
- Harness check: on a throwaway local merge of `origin/devin/mig-14-{policy,intake,workbench,
  settlement,payment,reports,admin}` into this branch, `-Pareas` gave 21/21 passing. That
  merge was not pushed. So the replay port extracts the same values as the Python harness.

## Decisions

1. Probe paths come from the probe key (e.g. `claim.N.*` → `/workbench/view.do?claimId=N`,
   `payment.*` → `/payment/history.do`). They are not read from `capture.py` because
   transcripts only store the keys. The mapping matches every probe path in `capture.py`
   SCENARIOS.
2. Redirects are followed, like urllib's default opener, so `result` is computed from the
   final response, exactly as `capture.py` records it.
3. Payment transcripts get their own class, `PaymentTranscriptTest`, even though `capture.py`
   files them under `settlement`, because the area streams are split that way.
4. The existing Wave 0 tests (`RepositoryIntegrationTest` etc.) are left untouched. The new
   suite is additive.
5. The existing `RepositoryIntegrationTest` still uses an in-memory database. The new
   repository test uses the committed seeded snapshot, so the real file database (with all
   constraints) is exercised.

## Legacy quirks pinned by tests (not fixed)

- Settlement: `1.005` → `1.00` (double `Math.round(x*100)/100`). The `ns` money formatter
  shows the covered `1.005` as `1.01`.
- Blank, whitespace or null deductible is `0`. A non-numeric deductible throws
  `NumberFormatException`.
- A deductible larger than the loss floors at `0.00`.
- The cap is strictly greater-than. Being capped sets `cappedAtLimit`, but a value exactly at
  the limit does not.
- Order is depreciation → deductible → floor → cap → round. The result echoes the raw inputs.
- A CLOSED claim (seed 120) is still calculable and saveable through `SettlementService`. The
  service gives it settlement id `claimId+10000`, `calculatedBy=supervisor` and date
  `2019-03-01`, and the claim status is unchanged.
- `normalizedDate`: lenient `02/30/2019` → `2019-03-02` and `13/15/2018` → `2019-01-15`.
  Blank, null, whitespace or unparseable input → `2019-04-01`.
- `validDateShape` checks the digit pattern only (`99/99/9999` passes). `integer` and
  `decimal` fall back on any parse failure. `money` is always US-locale with 2 decimals.
- `ns` field: money/integer/date/text formatting. Non-numeric values pass through. Only
  `& < > "` are escaped (apostrophes are not, and already-escaped text is escaped again).
  Money uses the default locale.
- Deterministic ids: next claim `121` (then `122`) and next payment `61`. The check number is
  `CHK-61`.
- `ClaimDAO.insert` writes six columns while `CLAIM.loss_date` (and `assigned_adjuster`) are
  NOT NULL, so it always fails against the real schema. That is why intake uses its own insert.
- `PolicyDAO.delete` does not cascade: it fails on the `INSURED_PARTY` foreign key.
- `PolicyDAO.findByLine` and `ClaimDAO.search` build SQL by string concatenation
  (SQL-injection finding, preserved). `ReportDAO.claimCounts(orderBy)` does the same.

## Security findings (recorded, preserved)

- `CredentialRuleTest.securityFinding_adjusterPrefixWithLegacyPrefixPasswordIsAcceptedForAnyUser`:
  any username starting with `adjuster` and any password starting with `legacy` is accepted,
  even for users not in the ADJUSTER table. Matching is case-sensitive, and `supervisor` must
  match exactly.
- The ADJUSTER table stores plaintext passwords (`AdjusterDAO.authenticate`).
- The concatenated SQL in the DAOs listed above.
