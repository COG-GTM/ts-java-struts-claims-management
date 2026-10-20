-- Explicit database preconditions of the payment parity scenarios (ADR-002,
-- "How parity is judged"). Each section is applied, after a fresh schema.sql
-- and seed.sql, by POST /parity/reset on the service (make payment-run-parity),
-- before the scenario is replayed. The same statements were applied to the
-- legacy database before its requests were recorded:
-- docs/specs/SPEC-PAY-001-proof.json (live:<case>) and transcripts/payment_*.json.
-- tools/parity/payment_parity.py fails when a live section differs from the
-- precondition recorded in the proof.

-- scenario: payment_issue
-- fresh seed (transcripts/payment_issue.json: first payment of claim 119)

-- scenario: payment_history
-- payment 61 issued by payment_issue, which make capture replays first
INSERT INTO PAYMENT VALUES (61, 119, 119, 'Reserved Claimant', 1000, 'CHECK', 'CHK-61', '2019-04-03', 'ISSUED');

-- scenario: issue-post
-- fresh seed

-- scenario: issue-get
-- fresh seed

-- scenario: issue-claim-fallback
-- fresh seed

-- scenario: issue-amount-fallback-seed
-- fresh seed

-- scenario: issue-newest-settlement
-- claim 119 has a second, newer settlement 121 of 1000
INSERT INTO SETTLEMENT VALUES (121, 119, 1500, 500, 0, FALSE, 1000, 'adjuster1', '2019-03-02');

-- scenario: issue-amount-raw
-- fresh seed

-- scenario: issue-check-number
-- fresh seed

-- scenario: issue-next-id
-- payment 500 exists for claim 1 (gap after 60)
INSERT INTO PAYMENT VALUES (500, 1, 1, 'Gap Payee', 1, 'CHECK', 'CHK-GAP', '2019-03-15', 'ISSUED');

-- scenario: issue-required-columns
-- fresh seed

-- scenario: issue-column-limits
-- fresh seed

-- scenario: issue-no-settlement
-- settlement 120 deleted, so claim 120 exists without a settlement
DELETE FROM SETTLEMENT WHERE settlement_id = 120;

-- scenario: issue-raw-text
-- fresh seed

-- scenario: issue-no-business-checks
-- fresh seed

-- scenario: history
-- fresh seed

-- scenario: history-order
-- payments 300 then 200 inserted for claim 2
INSERT INTO PAYMENT VALUES (300, 2, 2, 'Third', 3, 'CHECK', 'CHK-300', '2019-03-20', 'ISSUED');
INSERT INTO PAYMENT VALUES (200, 2, 2, 'Second', 2, 'CHECK', 'CHK-200', '2019-03-19', 'ISSUED');

-- scenario: detail
-- fresh seed

-- scenario: detail-default-61
-- payment 61 exists for claim 5
INSERT INTO PAYMENT VALUES (61, 5, 5, 'Payee 61', 61, 'EFT', 'CHK-61X', '2019-03-21', 'ISSUED');

-- scenario: remittance
-- fresh seed

-- scenario: remittance-total
-- claim 3 also has a VOID payment of 100
INSERT INTO PAYMENT VALUES (400, 3, 3, 'Void Payee', 100, 'CHECK', 'CHK-400', '2019-03-22', 'VOID');

-- scenario: unauthenticated
-- fresh seed

-- scenario: adjuster-issue
-- fresh seed

-- scenario: chg-002-above-settlement
-- claim 119 has a newer settlement 121 of 1000; the request pays 1000.01 (CHG-002)
INSERT INTO SETTLEMENT VALUES (121, 119, 1500, 500, 0, FALSE, 1000, 'adjuster1', '2019-03-02');
