# Known legacy quirks

Behaviour of the Struts monolith that is deliberately kept as it is. Each row
is proved by a transcript or a live request and is reproduced by the extracted
service unless a `docs/changes/CHG-nnn` record says otherwise. Nothing here is
a reason to edit `src/main`.

| Quirk | Legacy behaviour | Evidence | Service |
| --- | --- | --- | --- |
| Payment amount above the settlement | `PaymentIssueAction` takes any parseable `amount` and issues it, however far above the claim's latest `settlement_amount`; there is no comparison in the Action (`src/main/java/com/northstar/claims/web/PaymentIssueAction.java:28-31`) and no Struts validation on the mapping (`src/main/webapp/WEB-INF/struts-config.xml:223-231`). | Live L10 in `docs/specs/SPEC-PAY-001-live.json`: `amount=5000.00` against settlement 121 at 1000.00 is issued as `CHK-66`; L21, L22, L23, L36 issue positive amounts against settlements at 0. | Kept in the monolith. Not reproduced by `services/payment-service` since `docs/changes/CHG-002-payment-above-settlement.md` (PAY-R05 v2): the service answers `payment.amount.aboveSettlement`; parity reports those requests as CHANGED (CHG-002). |
| Negative and NaN payment amounts | `-250.00` and `NaN` are issued and stored as parsed (SPEC-PAY-001 PAY-R05). | Live L08, L11; `db_after.payment_rows_after_seed` in `docs/specs/SPEC-PAY-001-live.json`. | Reproduced (OQ-04 stays open). |
