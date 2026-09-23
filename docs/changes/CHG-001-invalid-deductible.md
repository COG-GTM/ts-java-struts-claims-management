# CHG-001 — An invalid deductible is a validation error, not the error screen

Status: Approved
Date: 2026-09-23
Applies to: `services/settlement-service` only. The Struts monolith under
`src/main` is not changed.
Rules affected: SETTLE-R06 (v1 superseded by v2), SETTLE-R26 (unchanged),
SETTLE-R09 (no longer holds for the service on this path)

## Why

A `deductible` that is not blank and does not parse as a number reaches
`Double.parseDouble` unguarded in the legacy calculator
(`src/main/java/com/northstar/claims/service/SettlementCalculator.java:28`),
and the resulting `NumberFormatException` is caught by the global exception
mapping and forwarded to `error.jsp`
(`src/main/webapp/WEB-INF/struts-config.xml:59-60`). The operator sees a system
error screen for what is an input mistake: the screen carries no business
fields and no validation errors (transcript `settlement_bad_deductible`), so
nothing on it says which field was wrong or that retyping it would help.

The extracted service is required to treat this as input validation instead.

## Before (legacy, and the service up to this change)

`POST /settlement/calculate` with `claimId=119`, `coveredAmount=5000.00`,
`deductible=abc`, `depreciation=0.00`:

* legacy: status 200, `forward:/WEB-INF/jsp/error.jsp`, no business fields, no
  validation errors (transcript `settlement_bad_deductible`);
* service: status 500, screen `error`, no business fields, no validation errors
  — parity on the status class per ADR-001 ("How parity is judged").

`POST /settlement/save` with the same deductible threw out of the calculator
before the insert, so nothing was written
(`src/main/java/com/northstar/claims/web/SettlementSaveAction.java:34-39`).

## After (service only)

Both endpoints check the deductible before calculating or writing:

* the value is blank (null, empty, or whitespace only) — unchanged, it counts
  as 0.00 and the calculation proceeds (SETTLE-R05);
* the value is non-blank and `Double.parseDouble` rejects it — the response is
  status 200, screen `calculate`, `fields` empty, and `errors` exactly
  `["settlement.deductible.invalid"]`;
* any other value — unchanged.

On `POST /settlement/save` the check runs before the claim lookup and before
the insert, so a save with an invalid deductible writes no SETTLEMENT row and
returns the same status 200 / `calculate` screen / single validation error. The
save answer is the calculate screen rather than the save screen because no
settlement was produced to show, matching the calculate response exactly.

Unchanged by this record: the arithmetic and its order, the rounding
(`Math.round(amount * 100.0) / 100.0`), the parameter fallbacks (claimId 119,
coveredAmount 5000, depreciation 0, blank deductible 0, missing claim or policy
limit 10000 on calculate), and the save-path failure for a missing claim, which
keeps reaching the error screen (SETTLE-R26).

## Rules affected

| Rule | Effect |
| --- | --- |
| SETTLE-R06 (v1) | Legacy text kept, marked superseded for the service |
| SETTLE-R06 (v2) | New text: status 200, calculate screen, no business fields, one validation error `settlement.deductible.invalid`, on calculate and on save, with no row written |
| SETTLE-R05 (v1) | Unchanged — blank still means 0.00 |
| SETTLE-R09 (v1) | Still describes the legacy (`validate="false"`, always empty errors); the service now emits one validation key on this one path |
| SETTLE-R26 (v1) | Unchanged — a missing claim on save still fails the request |

## Tests

* `SettlementCalculateEndpointTest.nonNumericDeductibleIsAValidationError`
  (CHG-001) — `deductible=abc` on calculate gives 200, screen `calculate`,
  empty `fields`, `errors` = `["settlement.deductible.invalid"]`.
* `SettlementCalculateEndpointTest.blankDeductibleForClaim120` — unchanged,
  proves blank still means 0.00 (SETTLE-R05, transcript
  `settlement_blank_deductible`).
* `SettlementSaveEndpointTest.nonNumericDeductibleIsAValidationErrorAndWritesNothing`
  (CHG-001) — `deductible=abc` on save gives the same response and the
  SETTLEMENT row count is unchanged.
* `SettlementSaveEndpointTest.saveAgainstAMissingClaimFails` — unchanged,
  proves SETTLE-R26 still reaches the error screen.
* `SettlementCalculatorTest` — unchanged; the calculator itself still throws on
  a non-numeric deductible, the check sits in front of it.

## Parity effect

`transcripts/settlement_bad_deductible` no longer matches the service: the
transcript records the error screen with no validation errors, the service
answers the calculate screen with one. The difference is registered in
`parity/routes.json` against this record, so `make parity` reports that
scenario as `CHANGED (CHG-001)` instead of `PASS`, and still fails if the
service answers anything other than the approved response above. Every other
settlement scenario stays `PASS`.

The transcripts themselves are untouched: they are written only by
`make capture` against the unchanged monolith, and `make capture` followed by
`git diff --exit-code transcripts/` is clean.
