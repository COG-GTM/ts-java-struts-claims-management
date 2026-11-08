# Workbench parity

## Mapping

| Legacy action | URL | Modern controller | JSP → template |
| --- | --- | --- | --- |
| WorkbenchListAction | /workbench/list.do | WorkbenchController.list | workbench/list.jsp → workbench/list.html |
| WorkbenchViewAction | /workbench/view.do | WorkbenchViewController.view | workbench/view.jsp → workbench/view.html (unchanged) |
| WorkbenchAssignAction | /workbench/assign.do | WorkbenchController.change | workbench/view.jsp → workbench/view.html |
| WorkbenchStatusAction | /workbench/status.do | WorkbenchController.change | workbench/view.jsp → workbench/view.html |
| WorkbenchReserveAction | /workbench/reserve.do | WorkbenchController.change | workbench/view.jsp → workbench/view.html |
| WorkbenchNoteAction | /workbench/note.do | WorkbenchController.change | workbench/view.jsp → workbench/view.html |
| WorkbenchNoteHistoryAction | /workbench/notes.do | WorkbenchController.history | workbench/notes.jsp → workbench/notes.html |
| WorkbenchReserveHistoryAction | /workbench/reserveHistory.do | WorkbenchController.history | workbench/reserveHistory.jsp → workbench/reserveHistory.html |
| WorkbenchStatusHistoryAction | /workbench/statusHistory.do | WorkbenchController.history | workbench/statusHistory.jsp → workbench/statusHistory.html |
| No mapped action | None | None | workbench/assignment.jsp → workbench/assignment.html; workbench/note.jsp → workbench/note.html |

All JSP markers include the full `/WEB-INF/jsp/` prefix. No new URL was invented for the unreachable assignment/note forms.

## Decisions and preserved findings

* No shared Wave 0 files changed. The view controller/template are untouched so probe output stays identical.
* WorkbenchForm is a string-valued record; action-specific raw parameters retain legacy names.
* Inline update SQL lives in WorkbenchUpdates using the existing Spring JDBC connection. Concatenated text SQL (including quote failures/injection risk) is intentionally retained; hardening is separate work.
* Every mapping uses validate=false. statusDyn requires status and amountDyn requires amount in validation.xml, but neither rule executes. A genuine validation-failure path cannot be added without breaking parity; integration coverage exercises malformed/empty input fallback and HTTP 200 global error forwarding instead. No fabricated ns:error marker is emitted.
* All HTTP methods accepted, no redirects, default claim 119 on absent/malformed ID.
* Assignment reads adjuster, NOT assignedAdjuster; empty/missing defaults to adjuster2. Status reads status (including the query string), default INVESTIGATING. Whitespace is not trimmed.
* Reserve parses double, falls back to 4500 for invalid/missing input, performs no nonnegative validation, and relies on the existing field fragment for display rounding.
* Mutation responses set only the mutated field plus claim and claimId; unrelated summary fields remain empty. Never reuse the full view handler after updates.
* Note defaults to Review completed and is never persisted. No new IDs are allocated.
* List includes exactly OPEN claims, in claim_id order, and falls back to an empty list on DAO failure.
* Notes use note_date order; a missing claim uses claim 119 summary but still queries notes for the requested ID.
* Reserve/status histories are always empty. Missing claims show UNKNOWN and zero reserve with no claim ID.
* Missing detail/mutation claim and SQL failures forward to the global error page with HTTP 200.

## Verification

* `make -C modernized build` and `make -C modernized test`: 13 tests, zero failures/errors/skips.
* Workbench replay: assign/status/reserve all PASS (3/3). Foundation replay: login PASS (1/1).
* Intake replay: 0/5 PASS. The same five diffs reproduce unchanged on base `origin/devin/mig-14-spring-boot` (`73c305c`) in a separate worktree: intake endpoints render the global error page. These are not workbench regressions.
* Manual legacy/modern HTTP comparisons used the assign pre-state snapshot and authenticated supervisor, with GET `/workbench/{page}.do?claimId=119`. Capture's status/result/fields/error extractors matched for list (50 fields), notes (3), note (1), reserveHistory (3), statusHistory (2), and unmapped assignment (error page, zero). The assignment template cannot be reached through an action in the legacy configuration.
* The view controller/template remain byte-identical to base. Browser appearance was not visually verified; the checks above compare the harness contract, not pixels.
