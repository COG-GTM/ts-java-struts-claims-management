-- docs/specs/SPEC-PAY-001-live.json db_after.payment_rows_after_seed:
-- settlement 121 and payments 61 to 62 as the live proof left them.
INSERT INTO SETTLEMENT VALUES (121, 119, 5000, 500, 0, FALSE, 1000, 'supervisor', '2019-04-01');
INSERT INTO PAYMENT VALUES (61, 119, 119, 'Reserved Claimant', 0.0E0, 'CHECK', 'CHK-61', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (62, 119, 121, 'Reserved Claimant', 1000.0E0, 'CHECK', 'CHK-62', '2019-04-03', 'ISSUED');
