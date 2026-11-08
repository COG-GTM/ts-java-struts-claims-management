# Wave 0 parity foundation

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

To be completed by area streams and integration QA. Wave 0 implements login and the workbench state probe; all other functional flows remain out of scope. Full parity is expected to report only login passing.
