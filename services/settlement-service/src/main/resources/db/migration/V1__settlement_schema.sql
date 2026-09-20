-- Settlement module schema, extracted from src/main/resources/db/schema.sql.
-- Only the three tables the settlement seam touches are carried over. Money
-- columns stay DOUBLE PRECISION because the legacy calculator and DAO work in
-- binary doubles (SETTLE-R28, SETTLE-R42); changing the column type would
-- change what is read back.

CREATE TABLE policy (
    policy_id        INTEGER          NOT NULL,
    policy_number    VARCHAR(30)      NOT NULL,
    line_of_business VARCHAR(40)      NOT NULL,
    insured_name     VARCHAR(100)     NOT NULL,
    insured_address  VARCHAR(200)     NOT NULL,
    effective_date   DATE             NOT NULL,
    expiry_date      DATE             NOT NULL,
    policy_limit     DOUBLE PRECISION NOT NULL,
    deductible       DOUBLE PRECISION NOT NULL,
    annual_premium   DOUBLE PRECISION NOT NULL,
    status           VARCHAR(20)      NOT NULL,
    CONSTRAINT pk_policy PRIMARY KEY (policy_id),
    CONSTRAINT uq_policy_number UNIQUE (policy_number)
);

CREATE TABLE claim (
    claim_id          INTEGER          NOT NULL,
    claim_number      VARCHAR(30)      NOT NULL,
    policy_id         INTEGER          NOT NULL,
    claimant_name     VARCHAR(100)     NOT NULL,
    loss_date         DATE             NOT NULL,
    reported_date     DATE             NOT NULL,
    loss_type         VARCHAR(40)      NOT NULL,
    description       VARCHAR(300)     NOT NULL,
    status            VARCHAR(30)      NOT NULL,
    reserve_amount    DOUBLE PRECISION NOT NULL,
    assigned_adjuster VARCHAR(40)      NOT NULL,
    created_by        VARCHAR(40)      NOT NULL,
    created_date      DATE             NOT NULL,
    CONSTRAINT pk_claim PRIMARY KEY (claim_id),
    CONSTRAINT uq_claim_number UNIQUE (claim_number),
    CONSTRAINT fk_claim_policy FOREIGN KEY (policy_id) REFERENCES policy (policy_id)
);

CREATE TABLE settlement (
    settlement_id      INTEGER          NOT NULL,
    claim_id           INTEGER          NOT NULL,
    covered_amount     DOUBLE PRECISION NOT NULL,
    deductible_applied DOUBLE PRECISION NOT NULL,
    depreciation       DOUBLE PRECISION NOT NULL,
    capped_at_limit    BOOLEAN          NOT NULL,
    settlement_amount  DOUBLE PRECISION NOT NULL,
    calculated_by      VARCHAR(40)      NOT NULL,
    calculated_date    DATE             NOT NULL,
    CONSTRAINT pk_settlement PRIMARY KEY (settlement_id),
    CONSTRAINT fk_settlement_claim FOREIGN KEY (claim_id) REFERENCES claim (claim_id)
);

CREATE INDEX ix_claim_policy ON claim (policy_id);
CREATE INDEX ix_settlement_claim ON settlement (claim_id);
