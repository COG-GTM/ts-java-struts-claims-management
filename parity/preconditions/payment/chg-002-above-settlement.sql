-- CHG-002 scenario payment_issue_above_settlement: claim 119's latest saved
-- settlement is 121 at 1000.00 (the row settlement_save writes, as in
-- capture-state.sql) and claim 119 has no payments yet. The scenario then
-- asks to pay 1500.00, which is above that settlement.
INSERT INTO SETTLEMENT VALUES (121, 119, 5000, 500, 0, TRUE, 1000, 'supervisor', '2019-04-01');
