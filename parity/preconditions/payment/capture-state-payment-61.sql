-- The row transcripts/payment_issue.json wrote, as transcripts/payment_history.json
-- found it: payment 61 for claim 119 against settlement 121 (capture-state.sql),
-- payee "Reserved Claimant", 1000.00, CHECK, CHK-61, 2019-04-03, ISSUED
-- (transcript business_fields and db_state payment.61.amount = 1000.00,
-- payment.61.status = ISSUED, payment.count.claim.119 = 1; PAY-R14, PAY-R15,
-- PAY-R16). Apply after capture-state.sql.
INSERT INTO PAYMENT VALUES (61, 119, 121, 'Reserved Claimant', 1000.0E0, 'CHECK', 'CHK-61', '2019-04-03', 'ISSUED');
