# Open questions for the settlement slice

Questions the code cannot answer. Each one names the rule it blocks, who can
answer it, and what we do until it is answered.

| Id | Question | Blocks | Owner | Interim decision | Status |
| --- | --- | --- | --- | --- | --- |
| OQ-01 | The Struts action falls back to a policy limit of `10000` when the claim or its policy is not found (`SettlementCalculateAction.java`, `double limit = 10000;`). Is that a business rule or a leftover default? | SETTLE-R05 | claims operations SME | Preserve the fallback in the new service, flag it in the known quirks register | open |
| OQ-02 | Every exception in the legacy application ends on `error.jsp` with HTTP 200 and no marker (QUIRK-05). Is a validation error for a non-numeric deductible acceptable to operations? | SETTLE-R06 | claims operations SME | Answered for the deductible case by CHG-001; other exceptions keep the legacy screen | answered (CHG-001) |
| OQ-03 | `calculated_date` on save is the constant `2019-04-01` (QUIRK-07). Should the service write the current date? | SETTLE-R12 | claims operations SME | Keep the constant so save parity holds | open |
| OQ-04 | The service has no login; `AuthFilter` is out of the slice (ADR-001, point 5), so `/settlement/detail` and `/settlement/save` answer any caller. Gateway, ported filter, or something else? | deployment of the slice | security architect | Service stays behind the parity harness only; raised by pull request review | open |
| OQ-05 | Legacy amounts are parsed with `Double.parseDouble` and have no length limit (QUIRK-08). Should the service cap input length, which the legacy screen does not? | SETTLE-R03, SETTLE-R04 | claims operations SME, security architect | Match legacy; a cap needs a change record | open |
