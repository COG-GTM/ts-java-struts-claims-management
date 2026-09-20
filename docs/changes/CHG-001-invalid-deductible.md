# CHG-001: reject a non-numeric deductible with a validation message

Rule changed: SETTLE-R06 (v1) becomes SETTLE-R06 v2
Specification: SPEC-SETTLE-001 v0.3 to v0.4
Status: implemented in the settlement service; legacy application unchanged
Raised by: the engineer, after reading `transcripts/settlement_bad_deductible.json`
Approved by: recorded here as the engineer's decision for the rehearsal; a
production change would carry the product owner's name and date

## Why

The legacy screen answers a deductible of `abc` with the generic system error
page (`error.jsp`, HTTP 200). The operator loses the form, sees no field
named, and the log records an unhandled `NumberFormatException`. This is the
one behaviour in the slice that nobody wants preserved.

## Before (SETTLE-R06 v1, kept in the legacy application)

`POST /settlement/calculate.do` with `deductible=abc` forwards to
`/WEB-INF/jsp/error.jsp`, status 200, no business fields, no validation
markers. Transcript: `settlement_bad_deductible`.

## After (SETTLE-R06 v2, settlement service only)

`POST /settlement/calculate` with a deductible that is not blank and does not
parse as a number answers status 200, screen `settlement/calculate`, no
business fields, and one validation error with key
`settlement.deductible.invalid`. Blank still means zero (SETTLE-R04). The save
endpoint applies the same check before writing.

## Parity

The legacy transcript still shows the old behaviour, so the parity harness
reports this scenario as `CHANGED (CHG-001)` rather than `FAIL`, using the
`approved_differences` entry in `parity/routes.json`:

```json
"settlement_bad_deductible": {
  "change": "CHG-001",
  "expect": {
    "result": "settlement/calculate",
    "validation_errors": ["settlement.deductible.invalid"]
  }
}
```

Any other difference on this scenario still fails.

## Tests

* `SettlementControllerTest.nonNumericDeductibleIsValidationError` (service)
* `LegacyCoercionsTest.nonNumericDeductibleFails` (service, unchanged: the
  low-level coercion still throws; the service layer turns it into the key)
* `SettlementCharacterizationTest.settlementBadDeductibleThrows` (legacy,
  unchanged: it pins v1 because the Struts application still does v1)

## Not changed

QUIRK-05 stays for every other exception. The Struts application is not
modified.
