-- The database make capture leaves behind when payment_issue runs: the seed
-- plus the SETTLEMENT row transcript settlement_save writes for claim 119
-- (5000.00 covered, 500.00 deductible, capped at policy 9001's 1000.00
-- limit). Read back from target/db/northstar after replaying settlement_save
-- against the legacy app from a fresh make seed. payment_issue's 1000.00
-- equals this settlement amount, so it is issued under CHG-002 as well.
INSERT INTO SETTLEMENT VALUES (121, 119, 5000, 500, 0, TRUE, 1000, 'supervisor', '2019-04-01');
