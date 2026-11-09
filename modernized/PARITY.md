# NorthStar Claims migration parity report

## Decisions

* **Snapshot-based area replay:** restore the stopped legacy database immediately before each scenario, so sequential dependencies do not require other functional areas to be implemented.
* **CSRF disabled:** the legacy harness posts without tokens. This is a security finding for post-parity hardening, not a production recommendation.
* **Login controller rather than formLogin redirect:** successful and rejected logins render HTTP 200, preserving LoginAction. The AuthenticationManager and session SecurityContext still enforce authentication.
* **Credential finding:** plaintext supervisor credentials and arbitrary adjuster*/legacy* prefix credentials are intentionally preserved. Do not silently strengthen them during parity migration.
* **Workbench view in Wave 0:** this read-only screen is shared harness infrastructure; ownership transfers to the workbench stream.
* **Services ported in Wave 0:** calculator, SettlementService, ClaimManager and PolicyManager are injected Spring beans. Arithmetic and DAO SQL remain legacy-compatible.
* **Default-locale field formatting:** money in FieldTag uses JVM default locale, not a forced US locale. Support's separate `money(double)` helper remains explicitly US, as in the legacy implementation. Launch both apps with the same locale.
* **Global errors:** Struts' configured global exception forwards to error.jsp without changing response status; advice preserves HTTP 200 plus the error view marker and Claims System Error text.
* **Process ownership:** parity tools stop only their own processes; an already-running target must be stopped by its owner first. This prevents probing or replacing the wrong live database.
* **Missing workbench claim:** a live legacy request with claimId=-1 returned HTTP 200 and the error.jsp marker (bean:write cannot resolve the null claim). The modern view returns that same error page with no business fields, retaining the harness's false existence probe.

## Scenario matrix

Generated from the fresh simultaneous captures in `/tmp/parity/legacy` and `/tmp/parity/modern` on 2026-11-09. Each row also matches its committed `transcripts/<scenario>.json` byte-for-byte, including fields, validation keys and database probes, not just the status/view shown here. Both index.json files match the committed index.

| scenario | area | legacy (status/result) | modern (status/result) | result |
| --- | --- | --- | --- | --- |
| login | foundation | 200 / forward:/WEB-INF/jsp/home.jsp | 200 / forward:/WEB-INF/jsp/home.jsp | MATCH |
| policy_search | policy | 200 / forward:/WEB-INF/jsp/policy/search.jsp | 200 / forward:/WEB-INF/jsp/policy/search.jsp | MATCH |
| policy_view | policy | 200 / forward:/WEB-INF/jsp/policy/view.jsp | 200 / forward:/WEB-INF/jsp/policy/view.jsp | MATCH |
| fnol_submit | intake | 200 / forward:/WEB-INF/jsp/intake/confirm.jsp | 200 / forward:/WEB-INF/jsp/intake/confirm.jsp | MATCH |
| workbench_assign | workbench | 200 / forward:/WEB-INF/jsp/workbench/view.jsp | 200 / forward:/WEB-INF/jsp/workbench/view.jsp | MATCH |
| workbench_status | workbench | 200 / forward:/WEB-INF/jsp/workbench/view.jsp | 200 / forward:/WEB-INF/jsp/workbench/view.jsp | MATCH |
| workbench_reserve | workbench | 200 / forward:/WEB-INF/jsp/workbench/view.jsp | 200 / forward:/WEB-INF/jsp/workbench/view.jsp | MATCH |
| settlement_calculate | settlement | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | MATCH |
| settlement_save | settlement | 200 / forward:/WEB-INF/jsp/settlement/save.jsp | 200 / forward:/WEB-INF/jsp/settlement/save.jsp | MATCH |
| payment_issue | payment | 200 / forward:/WEB-INF/jsp/payment/issue.jsp | 200 / forward:/WEB-INF/jsp/payment/issue.jsp | MATCH |
| payment_history | payment | 200 / forward:/WEB-INF/jsp/payment/history.jsp | 200 / forward:/WEB-INF/jsp/payment/history.jsp | MATCH |
| report_open_by_adjuster | reports | 200 / forward:/WEB-INF/jsp/report/openByAdjuster.jsp | 200 / forward:/WEB-INF/jsp/report/openByAdjuster.jsp | MATCH |
| report_loss_ratio | reports | 200 / forward:/WEB-INF/jsp/report/lossRatio.jsp | 200 / forward:/WEB-INF/jsp/report/lossRatio.jsp | MATCH |
| report_aged_claims | reports | 200 / forward:/WEB-INF/jsp/report/agedClaims.jsp | 200 / forward:/WEB-INF/jsp/report/agedClaims.jsp | MATCH |
| settlement_blank_deductible | settlement | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | MATCH |
| intake_lenient_date | intake | 200 / forward:/WEB-INF/jsp/intake/confirm.jsp | 200 / forward:/WEB-INF/jsp/intake/confirm.jsp | MATCH |
| settlement_half_cent | settlement | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | MATCH |
| settlement_policy_cap | settlement | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | MATCH |
| settlement_deductible_floor | settlement | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | 200 / forward:/WEB-INF/jsp/settlement/calculate.jsp | MATCH |
| intake_missing_claimant | intake | 200 / forward:/WEB-INF/jsp/intake/new.jsp | 200 / forward:/WEB-INF/jsp/intake/new.jsp | MATCH |
| intake_missing_description | intake | 200 / forward:/WEB-INF/jsp/intake/new.jsp | 200 / forward:/WEB-INF/jsp/intake/new.jsp | MATCH |
| intake_bad_date | intake | 200 / forward:/WEB-INF/jsp/intake/new.jsp | 200 / forward:/WEB-INF/jsp/intake/new.jsp | MATCH |

Admin has no committed harness scenario; it is covered by `AdminIntegrationTest` and the child's separate [live HTTP comparison](docs/areas/admin-parity.md). The 22-scenario claim is not a visual/full-HTML equivalence claim.

## Quirks preserved

Test names below refer to `src/test/java/com/northstar/claims/`; transcript names refer to the repository's `transcripts/` directory.

| quirk | where pinned (transcript/test) | legacy behaviour | modern behaviour | decision |
| --- | --- | --- | --- | --- |
| Half-cent rounding | settlement_half_cent; tests/unit/SettlementCalculatorQuirksTest | 1.005 settles to 1.00 using double Math.round(x*100)/100 | Same; the separate money formatter displays raw covered 1.005 as 1.01 | Preserve double arithmetic, not BigDecimal |
| Blank deductible | settlement_blank_deductible; SettlementCalculatorQuirksTest | Blank, whitespace and null = 0, not policy deductible | Same | Preserve |
| Deductible floor | settlement_deductible_floor | Negative net floors to 0.00 | Same | Preserve order: depreciation, deductible, floor, cap, round |
| Policy cap | settlement_policy_cap; SettlementCalculatorQuirksTest | Amount above limit is capped and cappedAtLimit=true; exactly equal is not capped | Same | Policy record supplies cap; submitted limit ignored |
| Lenient February date | intake_lenient_date; tests/unit/ClaimsSupportHelpersTest | 02/30/2019 becomes 2019-03-02 | Same | Keep lenient parsing |
| Blank date | ClaimsSupportHelpersTest; intake/IntakeIntegrationTest | Blank/missing loss date defaults to 2019-04-01 | Same | Do not introduce required-date validation |
| Validation forwards | intake_missing_claimant, intake_missing_description, intake_bad_date | HTTP 200 new.jsp; ordered errors.claimant.required, errors.description.required or errors.lossdate.format as applicable | Identical resource keys, order, status and marker; no insert | Keep manual validation, not new MVC 400 responses |
| Deterministic IDs | fnol_submit, intake_lenient_date, payment_issue; tests/repository/SeededRepositoryIntegrationTest | Claims 121/122; payment 61; CHK-61 from max+1 and CHK-prefix | Same | Preserve fixtures and ordering; concurrency hardening is separate |
| Fixed payment date | payment/PaymentIntegrationTest; payment_issue | issuedDate=2019-04-03 | Same | Retain deterministic date; harness payment_issue does not itself expose date |
| Report date and buckets | report_aged_claims; reports/ReportsIntegrationTest | As-of 2019-04-01, inclusive 30/60/90 buckets, excluding CLOSED/DENIED | Same; aged reserve display still truncates to integer | Preserve DAO SQL and property |
| CLOSED claim calculation | settlement_blank_deductible; SettlementCalculatorQuirksTest.closedClaimIsStillCalculableAtServiceLevel | CLOSED seed claim 120 remains calculable/saveable | Same | Do not add workflow rejection |
| Field encoding and coercion | tests/unit/NsFieldFormatterTest; FieldFormatterTest | String coercion, only & < > double-quote escaped, nonnumeric passthrough, default-locale money | Same | Share fragments; do not use default Thymeleaf escaping for ns fields |
| Payment dependency in reports | payment_issue then report_loss_ratio | Payment 61 contributes 1000 to AUTO losses, giving 46270.00 and ratio 0.51 at that step | Same once Payment merged | Never fake report totals when an upstream area is absent |

## Per-area decisions

Summarized from the structured child RESULTS array in `/home/ubuntu/.devin-files/devin-remote-overflows-1000/526ba9b9/content.txt`. These are historical child decisions plus the integration updates noted explicitly below; child CI/visual claims are not substituted for the current verification.

* **Policy — [PR #47](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/47), [details](docs/areas/policy.md):** one controller covers seven URLs; all-string form records preserve parse defaults. Reuse PolicyDAO SQL/order, empty coverage lists, swallowed search/list errors, fixed unsaved insured-party values, and renewal fallback only on missing row. Null/empty business line defaults to AUTO; whitespace is not trimmed. No new validation keys. SQL concatenation is carried forward, not silently corrected.
* **Intake — [PR #46](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/46), [details](docs/areas/intake.md):** area-specific IntakeRepository inserts the full legacy column set; shared ClaimDAO.insert remains unchanged. Preserve ordered manual validation, no writes/session changes on errors, raw claimant/description, ignored policy/lossType inputs, hard-coded defaults, and confirmation's request-ID echo. Legacy unrestricted methods and broken relative form action remain parity decisions.
* **Workbench — [PR #48](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/48), [details](docs/areas/workbench.md):** mutations/history use one controller and WorkbenchUpdates with the existing pool; Wave 0 read-only view is retained. assign consumes `adjuster`, not `assignedAdjuster`, defaulting to adjuster2; reserve defaults to 4500. note echoes text without inserting; history lists stay empty where legacy does. Preserve sparse mutation fields, unrestricted methods, concatenated updates, and HTTP-200 errors rather than invented validation.
* **Settlement — [PR #50](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/50), [details](docs/areas/settlement.md):** three URLs share all-string binding and Wave 0 calculator/DAOs. Policy record controls cap; calculation can fall back to 10000, whereas saving a missing claim/policy errors. Save uses max+1 IDs, session operator and fixed 2019-04-01; the separately ported SettlementService retains its different claimId+10000 / supervisor / 2019-03-01 conventions. Detail chooses highest ID. Missing settlement and broken links preserve legacy errors, not new validations.
* **Payment — [PR #45](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/45), [details](docs/areas/payment.md):** four URLs consume raw request parameters, reuse PaymentDAO/SettlementDAO/support, and need no form record/new SQL. Preserve missing-settlement error, max+1 race, fixed date/check number, amount parse fallback, all-status remittance sums, hard-coded history links, null payee/method database errors, and negative/repeat issuance. No new validation or method restrictions in the parity change.
* **Reports — [PR #49](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/49), [details](docs/areas/reports.md):** eight endpoints reuse shared DAOs/fragments without artificial forms. Preserve join multiplication, all payment statuses, zero-premium fallback, integer aged reserve formatting, blank unpopulated workload/reconciliation fields, eleven index links for seven destinations, and OPEN-only claim-aging detail. Payment-dependent sequential totals now match after merging Payment.
* **Admin — [PR #51](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/51), [details](docs/areas/admin.md):** area-local filter only rewrites anonymous login Location to legacy absolute form; no shared access-control change. Retain broken module-relative forwards (adjusters/reference return 404 error marker), unmapped editAdjuster returning 500, ignored form fields, no mutations or role restriction, and converted but unreachable templates. The child's 22 live variants are documented separately; they are not extra golden scenarios.
* **Tests — [PR #52](https://github.com/COG-GTM/ts-java-struts-claims-management/pull/52), [details](docs/areas/tests.md):** additive unit, real seeded-file repository and transcript suites; snapshots are swapped with SHUTDOWN in one shared RANDOM_PORT context using a test-only nonpooling datasource. Java extraction mirrors capture.py; Payment has its own class despite legacy index grouping. **Integration update:** all area-tagged tests now run by default (105 total); `-Pareas` remains a focused 21-test subset. No fixture/harness loosening.

## Security findings carried forward (follow-up ticket)

These are recommended follow-up ticket items, not newly created tickets or fixes shipped in this parity migration.

| finding | evidence / affected area | recommended fix |
| --- | --- | --- |
| Plaintext and prefix-match credentials | SecurityConfiguration; ADJUSTER.password; CredentialRuleTest; policy/tests child findings | Authenticate provisioned active users against adaptive password hashes (Argon2/bcrypt); remove adjuster*/legacy* acceptance, rotate legacy credentials, and migrate stored passwords |
| CSRF disabled for harness parity | SecurityConfiguration and intake/workbench/settlement/payment child findings | Enable Spring Security CSRF tokens and update forms/harness in a separately versioned contract change |
| State-changing GET requests | Intake submission, workbench mutations, settlement save, payment issue | Require POST for mutations, reject GET, retain CSRF protection, and test cross-site requests |
| Policy search SQL injection | PolicyDAO.findByLine; policy/tests child findings | Replace concatenated values with bound JDBC parameters and add injection regression tests |
| Other dynamic SQL | ClaimDAO.search; ReportDAO.claimCounts(orderBy); WorkbenchUpdates/ClaimsSupport.update; workbench/tests child findings | Bind all values; allowlist identifiers and sort expressions rather than interpolating request-supplied text; test quote/error cases |
| Missing admin role restriction | Admin child reports any authenticated operator can access admin URLs | Introduce explicit roles and method/URL authorization with least-privilege tests; define intended admin behavior before making broken legacy screens reachable |
| Payment integrity and ID race | Payment child: negative/repeat payments, no validation, max+1 IDs | Validate finite positive amounts and settlement balance, add idempotency keys, allocate IDs with sequences/identity, and make issuance transactional with concurrency tests |

## Verification

Run from the repository root except Maven packaging, which runs in `modernized/`. JDK 17 is used only for legacy; modern Makefile exports JDK 21.

| exact command | result |
| --- | --- |
| `make -C modernized test` | 105 tests; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, including area transcripts by default |
| `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 make test` | 9 tests; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| `cd modernized && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B -DskipTests package` | BUILD SUCCESS; packages the already-tested merged sources |
| `make -C modernized replay AREA=all` | 22/22 PASS from individual legacy pre-state snapshots |
| `bash modernized/parity/side_by_side.sh` | 22/22 byte-identical; index.json also identical; both real file-backed servers left running |

The side-by-side script executes these commands after asserting ports 8080 and 8081 are unused and checking the built modern jar exists:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 make seed
make -C modernized seed
# Detached launchers, with log/PID files under modernized/target/side-by-side:
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 make run
make -C modernized run
python3 tools/capture/capture.py --base-url http://localhost:8080/claims --out-dir /tmp/parity/legacy
python3 tools/capture/capture.py --base-url http://localhost:8081/claims --out-dir /tmp/parity/modern
diff -r /tmp/parity/legacy /tmp/parity/modern
diff -r --exclude=README.md transcripts /tmp/parity/legacy
diff -r --exclude=README.md transcripts /tmp/parity/modern
```

All three diffs exited 0 with no output. README.md is excluded because it is documentation, not generated JSON; all 22 scenario JSONs and index.json are compared. Both apps remain at their post-capture sequential state (claims 121/122 and payment 61 exist), available at http://localhost:8080/claims/login.do and http://localhost:8081/claims/login.do for the requested QA recording. Do not reseed or run destructive build/replay commands while recording.

Legacy `src/`, pom.xml, Makefile, build.xml, transcripts/ and tools/ remain unchanged from Wave 0 (`73c305c`). Existing parity scripts/snapshots also remain unchanged; only side_by_side.sh was added. No behavior seam needed correction after the Payment merge. Browser visual QA and an annotated recording are not included in these automated results and remain to be performed; no testing-agent tool is available in this session.
