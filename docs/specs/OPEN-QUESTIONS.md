# Open questions

Questions raised while writing the settlement specs that need a business answer.

| Id | Question | Blocks | Owner | Interim decision | Status |
| --- | --- | --- | --- | --- | --- |
| OQ-01 | Is the 10000 policy-limit fallback used when a claim or its policy cannot be found (SPEC-SETTLE-001 SETTLE-R08 v2) a business rule, or a leftover default in the code? | SPEC-SETTLE-001 SETTLE-R08 | Claims operations SME | Keep the 10000 fallback in the new service and record it in the quirks register. | Open |
| OQ-02 | Two payment issue requests at the same moment both read `max(payment_id)` before either inserts (SPEC-PAY-001 PAY-R06). Does the second fail on the primary key, and is that acceptable? Not reproduced against the legacy app, so not a rule. | SPEC-PAY-001 PAY-R06 | Claims operations SME | Treat a concurrent duplicate id as unspecified; do not design for it until answered. | Open |
| OQ-03 | Every payment is issued on 2019-04-03 whatever the day (SPEC-PAY-001 PAY-R08). Is that the business "as of" date or a leftover constant? The behaviour is proved; the intent is not. | SPEC-PAY-001 PAY-R08 | Claims operations SME | Keep 2019-04-03 exactly as legacy does. | Open |
| OQ-04 | Negative, NaN and above-settlement amounts are issued without checks (SPEC-PAY-001 PAY-R05), and the history page always links to payment 61 (SPEC-PAY-001 PAY-R15). Are these intended? The behaviour is proved; the intent is not. | SPEC-PAY-001 PAY-R05, PAY-R15 | Claims operations SME | Keep legacy behaviour; any change needs a CHG record. | Open |
