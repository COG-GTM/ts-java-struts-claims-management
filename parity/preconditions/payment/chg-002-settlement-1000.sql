-- CHG-002 (docs/changes/CHG-002-payment-above-settlement.md), scenario
-- payment_above_settlement_rejected: settlement 121 at 1000.00 is the latest
-- settlement of claim 119 and no payment above the seed's 60 exists, so an
-- accepted request would take payment_id 61 (PAY-R06). Same row as live L10's
-- settlement in docs/specs/SPEC-PAY-001-live.json, without its payments.
INSERT INTO SETTLEMENT VALUES (121, 119, 5000, 500, 0, FALSE, 1000, 'supervisor', '2019-04-01');
