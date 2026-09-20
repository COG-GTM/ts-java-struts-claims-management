-- Service-owned slice of the legacy schema: only the tables the settlement screens read
-- (POLICY.policy_limit for the cap, CLAIM.policy_id for the lookup) and the settlement table
-- they write (SETTLE-R14, SETTLE-R38, SETTLE-R47).

create table policy (
    policy_id     integer primary key,
    policy_number varchar(32)    not null,
    policy_limit  numeric(14, 2) not null,
    deductible    numeric(14, 2) not null,
    status        varchar(16)    not null
);

create table claim (
    claim_id     integer primary key,
    claim_number varchar(32)    not null,
    policy_id    integer        not null references policy (policy_id),
    status       varchar(16)    not null,
    reserve      numeric(14, 2) not null
);

create table settlement (
    settlement_id      integer primary key,
    claim_id           integer        not null references claim (claim_id),
    covered_amount     numeric(14, 2) not null,
    deductible_applied numeric(14, 2) not null,
    depreciation       numeric(14, 2) not null,
    capped_at_limit    boolean        not null,
    settlement_amount  numeric(14, 2) not null,
    calculated_by      varchar(32),
    calculated_date    date
);

create index settlement_claim_idx on settlement (claim_id, settlement_id desc);
