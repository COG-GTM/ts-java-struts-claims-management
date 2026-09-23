# Open questions

Questions raised while writing the settlement specifications. Each entry names
what it blocks, who can answer it, and the decision the migration proceeds on
until it is answered.

| Id | Question | Blocks | Owner | Interim decision | Status |
| --- | --- | --- | --- | --- | --- |
| OQ-01 | Is the `10000` policy-limit fallback in `SettlementCalculateAction` a business rule, or a leftover default? It applies whenever the claim or its policy cannot be loaded, and it can itself cap the settlement (SPEC-SETTLE-001 SETTLE-R05v2). | SPEC-SETTLE-001 SETTLE-R05v2 | Claims operations SME | Preserve the fallback in the new service and record it in the quirks register. | Open |
