# CHG-001 — The error path answers with an error status class

Status: Approved
Date: 2026-09-23
Scope: settlement service (`services/settlement-service`), error path only.

## Difference

| | Legacy | Settlement service |
| --- | --- | --- |
| Status | 200 | 500 |
| Status class | 2xx | 5xx |
| Business fields | none | none |
| Validation keys | none | none |

Exercised by transcript `settlement_bad_deductible` (SETTLE-R06): a non-empty,
non-numeric `deductible` reaches `Double.parseDouble` unguarded, and the
monolith's global exception mapping forwards to `/WEB-INF/jsp/error.jsp` —
which, being a forward, keeps status 200
(`src/main/webapp/WEB-INF/struts-config.xml:59-60`). The same input on the
service reaches `SettlementErrorHandler`, which answers 500 with the `error`
screen and no fields.

## Why it is approved

ADR-001 ("How each Struts piece maps", "How parity is judged") decides that the
global exception mapping to `error.jsp` becomes "an exception handler returning
the equivalent error status class", and that parity is judged on the status
class rather than the exact Struts-forwarded 200. Reproducing the 200 would
mean emitting a success status for a failed request over an API that has no
HTML forward to carry the error, which is the presentation detail ADR-001
excludes from parity.

The same rule applies to SETTLE-R26 (a save against a missing claim), which
reaches the same handler; no transcript records that path today.

## Not changed

The response screen (`error`), the empty business fields and the empty
validation keys all match the legacy behaviour. Nothing else about the error
path is altered: the set of inputs that fail is exactly the legacy set.
