# Known legacy quirks kept on purpose

Legacy behaviour that looks wrong but is reproduced by an extracted service
because no `docs/changes/CHG-nnn` record approves changing it (ADR-001 and
ADR-002, "Behaviour freeze"). Each entry names the rule that pins it.

## Payment (SPEC-PAY-001, services/payment-service)

| Quirk | Rule | Evidence |
| --- | --- | --- |
| `issue.do` writes a payment on GET | PAY-R02 | live:issue-get#1 |
| Missing or invalid `claimId` pays claim 119 | PAY-R04 | live:issue-claim-fallback#1-#3 |
| Missing or invalid `amount` pays the settlement amount (0.00 for claim 119) | PAY-R07 | live:issue-amount-fallback-seed#1-#3 |
| Negative, exponent, `NaN` and three-decimal amounts are stored as parsed | PAY-R08 | live:issue-amount-raw#1-#5 |
| Issued date is always `2019-04-03` | PAY-R11 | live:issue-post#1 |
| No business checks: closed claims, overpayment and repeats are accepted | PAY-R15 | live:issue-no-business-checks#1-#3 |
| History links always open payment 61 | PAY-R18 | live:history#1 |
| `detail.do` without `paymentId` opens payment 61, or the error page | PAY-R20 | live:detail-default-61#1; live:detail#2 |
| Total issued sums every status, VOID included | PAY-R23 | live:remittance-total#1 |
