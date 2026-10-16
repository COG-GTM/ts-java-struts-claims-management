-- The state transcripts/payment_history.json was captured in:
-- settlement 121 (1000.00) and the payment transcripts/payment_issue.json
-- issued against it (db_state payment.61.amount 1000.00).
INSERT INTO SETTLEMENT VALUES (121, 119, 5000, 500, 0, FALSE, 1000, 'supervisor', '2019-04-01');
INSERT INTO PAYMENT VALUES (61, 119, 121, 'Reserved Claimant', 1000.0E0, 'CHECK', 'CHK-61', '2019-04-03', 'ISSUED');
