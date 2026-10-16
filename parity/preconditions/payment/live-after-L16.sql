-- Database state of the SPEC-PAY-001 live run after step L16, on top of the
-- legacy seed (payments 1-60, settlements 118-120). Every row is copied from
-- the read-only JDBC probe in docs/specs/SPEC-PAY-001.md ("Read-only JDBC probe
-- after the run", rows 61-63), also in docs/specs/SPEC-PAY-001-live.json "db".
-- Applied by POST /parity/reset on the payment service (parity profile) before
-- the scenarios in parity/routes.json that name this file.
INSERT INTO PAYMENT VALUES (61, 118, 118, 'Live Payee', 1118.0E0, 'wire', 'CHK-61', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (62, 118, 118, 'Second Payee', 1118.0E0, 'CHECK', 'CHK-62', '2019-04-03', 'ISSUED');
INSERT INTO PAYMENT VALUES (63, 119, 119, 'Third Payee', 12.345E0, 'CHECK', 'CHK-63', '2019-04-03', 'ISSUED');
