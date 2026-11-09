# Settlement migration

## Decisions

* The controller and string-valued form record live in web.settlement. All three
  URLs accept GET and POST, preserve parameter names and render HTTP 200 forwards.
* struts-config disables validation on all three actions; validation.xml has no
  settlementForm entry and BaseForm validation is empty. No new validation or
  error keys are introduced. Exceptions use the existing global 200 error forward.
* Shared PolicyDAO, SettlementDAO, ClaimsSupport and SettlementCalculator are
  reused without edits. The actions have no area-specific inline SQL.
* Templates retain JSP view paths, resource keys and raw field values/types,
  including the calculate screen's unusual money type for the boolean cap flag.
* Save recalculates, uses MAX(settlement_id)+1, the session user and 2019-04-01.
  Detail selects the highest settlement ID, not the latest date.

## Preserved quirks and legacy bugs

* CLOSED (and denied) claims are calculable and saveable; there is no status gate.
* Floating-point Math.round arithmetic preserves 1.005 -> 1.00, not decimal half-up.
* Blank or absent deductible is zero; whitespace is also treated as zero by the
  calculator. Invalid nonblank deductible throws rather than validating.
* Negative net amounts floor at zero, then the policy cap applies; cappedAtLimit
  is true only when the net amount exceeds the limit (not on equality).
* claimId defaults to 119, coveredAmount to 5000, depreciation to zero on missing
  or unparsable input. Submitted policyLimit is ignored.
* Calculate falls back to a 10000 limit for a missing claim or policy. Save does
  not: null dereferences forward to the global error page without writing.
* Missing detail settlement reaches legacy bean:write's null-bean error; the port
  explicitly renders the global error view rather than an empty detail.
* The calculate detail link has an empty claimId because the legacy action never
  publishes that request attribute. The save link drops calculation parameters
  and recalculates defaults. Both links remain unchanged.
* MAX+1 IDs are deterministic but race-prone; no concurrency fix is introduced.

## Verification

SettlementIntegrationTest exercises real in-memory JDBC persistence through
authenticated MockMvc requests, including closed claims, defaults, rounding,
blank deductible, cap, floor, deterministic IDs, latest detail and error forwards.
The six settlement replays compare the packaged app against legacy snapshots.
