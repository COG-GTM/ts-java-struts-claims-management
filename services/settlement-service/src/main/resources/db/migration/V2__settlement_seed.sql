-- The claim and policy rows the six settlement transcripts need, with the values
-- com.northstar.claims.util.DatabaseBootstrap loads from src/main/resources/db/seed.sql
-- (policies 9001 and 9002, claims 119 and 120) and the two settlement rows seeded with them.

insert into policy (policy_id, policy_number, policy_limit, deductible, status) values
    (9001, 'NS-09001', 1000.00, 100.00, 'ACTIVE'),
    (9002, 'NS-09002', 100000.00, 5000.00, 'ACTIVE');

insert into claim (claim_id, claim_number, policy_id, status, reserve) values
    (119, 'CLM-00119', 9001, 'DENIED', 1500.00),
    (120, 'CLM-00120', 9002, 'CLOSED', 2000.00);

insert into settlement (settlement_id, claim_id, covered_amount, deductible_applied, depreciation,
                        capped_at_limit, settlement_amount, calculated_by, calculated_date) values
    (119, 119, 1500.00, 500.00, 100.00, false, 0.00, 'adjuster1', date '2019-03-01'),
    (120, 120, 2000.00, 5000.00, 100.00, false, 0.00, 'adjuster1', date '2019-03-01');
