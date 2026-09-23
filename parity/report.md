# Parity report — settlement module vs settlement-service

Result: PASS — 6 PASS, 1 CHANGED, 0 FAIL, 2 SKIP (http://localhost:8083, parity/replay.py at fc6be1a).

Compared per ADR-001: status class, business fields, validation keys and
the `db_state` probes read back through the service. HTML is not compared.

| Scenario | Verdict | SETTLE-R rules exercised | Detail |
| --- | --- | --- | --- |
| `settlement_calculate` | PASS | SETTLE-R01, SETTLE-R02, SETTLE-R03, SETTLE-R04, SETTLE-R07, SETTLE-R09, SETTLE-R11, SETTLE-R13, SETTLE-R14, SETTLE-R17, SETTLE-R18, SETTLE-R20, SETTLE-R22, SETTLE-R24 | - |
| `settlement_save` | PASS | SETTLE-R02, SETTLE-R03, SETTLE-R04, SETTLE-R09, SETTLE-R13, SETTLE-R25, SETTLE-R27, SETTLE-R28, SETTLE-R29, SETTLE-R30 | - |
| `payment_issue` | SKIP | - | Payment issue stays in the monolith: ADR-001 extracts only /settlement/calculate, /settlement/save and /settlement/detail; /claims/payment/issue.do has no service route. |
| `payment_history` | SKIP | - | Payment history stays in the monolith: /claims/payment/history.do has no service route (ADR-001 'Out of scope'). |
| `settlement_blank_deductible` | PASS | SETTLE-R01, SETTLE-R05, SETTLE-R07, SETTLE-R10, SETTLE-R14, SETTLE-R20, SETTLE-R22, SETTLE-R24 | - |
| `settlement_half_cent` | PASS | SETTLE-R01, SETTLE-R05, SETTLE-R14, SETTLE-R15, SETTLE-R16, SETTLE-R17, SETTLE-R20, SETTLE-R21, SETTLE-R24 | - |
| `settlement_policy_cap` | PASS | SETTLE-R01, SETTLE-R07, SETTLE-R11, SETTLE-R13, SETTLE-R14, SETTLE-R20, SETTLE-R22, SETTLE-R24 | - |
| `settlement_deductible_floor` | PASS | SETTLE-R01, SETTLE-R07, SETTLE-R11, SETTLE-R12, SETTLE-R14, SETTLE-R20, SETTLE-R22, SETTLE-R24 | - |
| `settlement_bad_deductible` | CHANGED | SETTLE-R06, SETTLE-R09 | CHG-001: status class: 2xx -> 5xx |

## Approved differences
* **CHG-001** (docs/changes/CHG-001-error-status-class.md) — status_class: legacy 2xx -> service 5xx. ADR-001 'How parity is judged' replaces the Struts forward to error.jsp (status 200) with the equivalent error status class; the screen, the empty business fields and the empty validation keys are unchanged.

## Probes not run
* `settlement_blank_deductible` — db claim.120.status not probed: The CLAIM table is outside the service boundary (ADR-001 'Out of scope'); the service exposes no claim-status read, and SETTLE-R24 records that the settlement paths never write it.
