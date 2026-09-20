-- Rows the six settlement transcripts depend on, copied value-for-value from
-- src/main/resources/db/seed.sql as loaded by
-- com.northstar.claims.util.DatabaseBootstrap.
--
-- Policy 9001 is the 1000.00 limit behind settlement_calculate,
-- settlement_save and settlement_policy_cap (SETTLE-R14); policy 9002 is the
-- 100000.00 limit behind the uncapped claim-120 scenarios.
INSERT INTO policy VALUES (9001, 'NS-09001', 'AUTO', 'Cap Trap Policy', '9001 Reserve Way', '2018-01-01', '2019-12-31', 1000, 100, 700, 'ACTIVE');
INSERT INTO policy VALUES (9002, 'NS-09002', 'HOMEOWNERS', 'Deductible Trap Policy', '9002 Reserve Way', '2018-01-01', '2019-12-31', 100000, 5000, 700, 'ACTIVE');

INSERT INTO claim VALUES (119, 'CLM-00119', 9001, 'Claimant 119', '2019-01-15', '2019-01-15', 'LIABILITY', 'Seeded claim 119', 'DENIED', 1500, 'adjuster4', 'supervisor', '2019-01-15');
INSERT INTO claim VALUES (120, 'CLM-00120', 9002, 'Claimant 120', '2019-01-15', '2018-12-01', 'COLLISION', 'Seeded claim 120', 'CLOSED', 2000, 'adjuster5', 'supervisor', '2018-12-01');

-- The legacy seed's highest settlement_id is 120, so the first save allocates
-- 121 in both systems (SETTLE-R39). These two rows also give /settlement/detail
-- something to show for the seeded claims (SETTLE-R03, SETTLE-R45).
INSERT INTO settlement VALUES (119, 119, 1500, 500, 100, FALSE, 0, 'adjuster1', '2019-03-01');
INSERT INTO settlement VALUES (120, 120, 2000, 5000, 100, FALSE, 0, 'adjuster1', '2019-03-01');
