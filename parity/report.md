# Parity report: settlement

Service: `http://localhost:8083`. Fixtures: `transcripts/settlement_*.json`.

Result: PASS 7, SKIP 2.

| Scenario | Rules | Verdict | Differences (legacy -> service) | Note |
| --- | --- | --- | --- | --- |
| settlement_calculate | SETTLE-R01, SETTLE-R05, SETTLE-R07, SETTLE-R08, SETTLE-R10, SETTLE-R11 | PASS |  |  |
| settlement_save | SETTLE-R12 | PASS |  |  |
| payment_issue |  | SKIP |  | screen not routed to this service |
| payment_history |  | SKIP |  | screen not routed to this service |
| settlement_blank_deductible | SETTLE-R04, SETTLE-R07 | PASS |  | db_state not checked (screen outside the slice): claim.120.status |
| settlement_half_cent | SETTLE-R09, SETTLE-R10 | PASS |  |  |
| settlement_policy_cap | SETTLE-R08 | PASS |  |  |
| settlement_deductible_floor | SETTLE-R07 | PASS |  |  |
| settlement_bad_deductible | SETTLE-R06 | PASS |  |  |
