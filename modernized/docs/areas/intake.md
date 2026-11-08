# MIG-14 — intake (FNOL)

## Scope

Ports `/intake/new.do`, `/intake/submit.do`, and `/intake/confirm.do` to
`web.intake.IntakeController` with an immutable request form. The inline
`IntakeSubmitAction` insert lives in the new `IntakeRepository`; all shared
Wave 0 files, legacy sources, transcripts, snapshots and parity tools stay unchanged.
Both templates reuse the existing layout and `ns` fragments with the original
`/WEB-INF/jsp/intake/{new,confirm}.jsp` marker paths and message keys.

## Decisions and preserved quirks

* The Struts mappings set `validate="false"`. Do not activate the required-date
  rule in `validation.xml`; preserve only the action's ordered claimant,
  description, and date-shape checks, including their exact resource keys.
* Missing/empty dates default to `2019-04-01`; nonempty dates must match
  `\d{2}/\d{2}/\d{4}` **without trimming**. Matching dates use the shared lenient
  parser (`02/30/2019` becomes `2019-03-02`).
* Validation forwards render HTTP 200, no redirect, no field markers, no writes,
  and no `lastLossDate` session update. The inputs stay empty, not repopulated.
* Preserve the unused new-screen metadata (`defaultLossDate=04/01/2019`, mode,
  title and screen name). The default date is not prefilled into the HTML input.
* Preserve the JSP's relative `action="intake/submit.do"`. On the intake page this
  resolves to `/claims/intake/intake/submit.do`, a legacy browser navigation bug.
  The valid action endpoint remains `/claims/intake/submit.do`; correcting the
  form is deferred until after parity rather than silently changing behavior.
* Like the Struts actions, mappings do not restrict the HTTP method. GET submit
  requests can still create claims; this is a post-parity security finding.
* Allocate IDs with the existing `max(claim_id)+1` helper (121/122 in the golden
  stateful chain). No sequence, transaction or race-condition fix is added.
* Keep submitted claimant/description strings untrimmed in the parameterized
  insert. Ignore supplied loss type, policy ID, claim ID and status: inserted rows
  use policy 9001, WATER, OPEN, zero reserve, adjuster1 and creator supervisor,
  regardless of the signed-in operator. All three dates use the normalized loss date.
* Submission forwards directly to confirmation and stores `lastLossDate` in the
  session. Direct confirmation only echoes the request ID with integer marker
  formatting: it does not load a claim or set claimStatus, which renders empty.
* No shared foundation changes or new dependencies are needed.

## Verification

`IntakeIntegrationTest` exercises rendered markers, new/confirm routes, real SQL
writes, ordered validation, raw strings, blank/missing dates, leniency, ID allocation,
ignored inputs, session state, unrestricted methods, and whitespace date rejection.

Required commands (JDK 21 through the modernized Makefile):

```
make -C modernized build
make -C modernized replay AREA=intake
make -C modernized test
```

The five golden scenarios are `fnol_submit`, `intake_lenient_date`,
`intake_missing_claimant`, `intake_missing_description`, and `intake_bad_date`.

Verified: build passed; modern tests passed (23 tests, including 13 intake cases).
The unmodified area replay returned:

| Scenario | Byte comparison |
| --- | --- |
| fnol_submit | PASS |
| intake_lenient_date | PASS |
| intake_missing_claimant | PASS |
| intake_missing_description | PASS |
| intake_bad_date | PASS |

**5/5 PASS**. The replay restored the committed pre-scenario database snapshot
before each request and used the unmodified capture extraction/serialization.
