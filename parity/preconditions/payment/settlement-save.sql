-- The SETTLEMENT row the legacy settlement save writes before the payment
-- requests: transcript settlement_save and live step L05 of
-- docs/specs/SPEC-PAY-001-live.json (claim 119, covered 5000.00, deductible
-- 500.00, depreciation 0.00, capped at the policy 9001 limit of 1000,
-- saved by supervisor on the frozen date 2019-04-01). The payment service does
-- not write SETTLEMENT, so parity applies this row as a precondition.
INSERT INTO SETTLEMENT VALUES (121, 119, 5000.0, 500.0, 0.0, TRUE, 1000.0, 'supervisor', '2019-04-01');
