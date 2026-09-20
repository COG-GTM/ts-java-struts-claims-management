# Open questions for the settlement slice

Questions the code cannot answer. Each one names the rule it blocks, who can
answer it, and what we do until it is answered.

| Id | Question | Blocks | Owner | Interim decision | Status |
| --- | --- | --- | --- | --- | --- |
| OQ-01 | The Struts action falls back to a policy limit of `10000` when the claim or its policy is not found (`SettlementCalculateAction.java`, `double limit = 10000;`). Is that a business rule or a leftover default? | SETTLE-R05 | claims operations SME | Preserve the fallback in the new service, flag it in the known quirks register | open |
