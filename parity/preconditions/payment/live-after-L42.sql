-- Database state of the SPEC-PAY-001 live run after step L42, on top of the
-- legacy seed (payments 1-60, settlements 118-120). Every row is copied from
-- the read-only JDBC probe in docs/specs/SPEC-PAY-001.md ("Read-only JDBC probe
-- after the run", rows 61-73 and SETTLEMENT 121), also in docs/specs/SPEC-PAY-001-live.json "db".
-- NaN is written as CAST('NaN' AS DOUBLE), the way HSQLDB accepts the value
-- that PaymentDAO.insert stored through setDouble (PAY-R08).
-- Applied by POST /parity/reset on the payment service (parity profile) before
-- the scenarios in parity/routes.json that name this file.
-- SETTLEMENT 121 is the settlement live step L42 saved for claim 118 (covered 3000,
-- deductible 250, depreciation 0, policy 38 limit 150000 so not capped, 2750.00,
-- savedBy supervisor, calculated_date 2019-04-01).
INSERT INTO SETTLEMENT VALUES (121, 118, 3000, 250, 0, FALSE, 2750, 'supervisor', '2019-04-01');
INSERT INTO PAYMENT VALUES (61, 118, 118, 'Live Payee', 1118.0E0, 'wire', 'CHK-61', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (62, 118, 118, 'Second Payee', 1118.0E0, 'CHECK', 'CHK-62', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (63, 119, 119, 'Third Payee', 12.345E0, 'CHECK', 'CHK-63', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (64, 120, 120, 'Zero Payee', 0.0E0, 'CHECK', 'CHK-64', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (65, 118, 118, '', 1.0E0, 'CHECK', 'CHK-65', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (66, 118, 118, 'Negative Payee', -5.0E0, 'CHECK', 'CHK-66', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (67, 118, 118, 'Get Payee', 7.0E0, 'CHECK', 'CHK-67', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (68, 118, 118, 'Sci Payee', 1000.0E0, 'CHECK', 'CHK-68', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (69, 118, 118, 'Comma Payee', 1118.0E0, 'CHECK', 'CHK-69', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (70, 118, 118, 'Space Payee', 5.0E0, 'CHECK', 'CHK-70', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (71, 118, 118, 'QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ', 1.0E0, 'NNNNNNNNNNNNNNNNNNNN', 'CHK-71', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (72, 118, 118, 'O''Brien <b>&</b> "Co"', 2.0E0, 'ach', 'CHK-72', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (73, 118, 118, 'NaN Payee', CAST('NaN' AS DOUBLE), 'CHECK', 'CHK-73', '2019-04-03', 'ISSUED');
