-- NorthStar Claims relational schema.
-- Dates remain SQL DATE values because the servlet tier uses java.util.Date.
DROP TABLE IF EXISTS PAYMENT;
DROP TABLE IF EXISTS SETTLEMENT;
DROP TABLE IF EXISTS RESERVE_HISTORY;
DROP TABLE IF EXISTS CLAIM_NOTE;
DROP TABLE IF EXISTS COVERAGE;
DROP TABLE IF EXISTS INSURED_PARTY;
DROP TABLE IF EXISTS CLAIM;
DROP TABLE IF EXISTS POLICY;
DROP TABLE IF EXISTS ADJUSTER;

CREATE TABLE POLICY (
    policy_id INTEGER NOT NULL,
    policy_number VARCHAR(30) NOT NULL,
    line_of_business VARCHAR(40) NOT NULL,
    insured_name VARCHAR(100) NOT NULL,
    insured_address VARCHAR(200) NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    policy_limit DOUBLE NOT NULL,
    deductible DOUBLE NOT NULL,
    annual_premium DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_policy PRIMARY KEY (policy_id),
    CONSTRAINT uq_policy_number UNIQUE (policy_number)
);

CREATE TABLE INSURED_PARTY (
    party_id INTEGER NOT NULL,
    policy_id INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    relationship VARCHAR(30) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(100),
    CONSTRAINT pk_insured_party PRIMARY KEY (party_id),
    CONSTRAINT fk_party_policy FOREIGN KEY (policy_id) REFERENCES POLICY(policy_id)
);

CREATE TABLE COVERAGE (
    coverage_id INTEGER NOT NULL,
    policy_id INTEGER NOT NULL,
    coverage_code VARCHAR(30) NOT NULL,
    description VARCHAR(200) NOT NULL,
    coverage_limit DOUBLE NOT NULL,
    deductible DOUBLE NOT NULL,
    CONSTRAINT pk_coverage PRIMARY KEY (coverage_id),
    CONSTRAINT fk_coverage_policy FOREIGN KEY (policy_id) REFERENCES POLICY(policy_id)
);

CREATE TABLE CLAIM (
    claim_id INTEGER NOT NULL,
    claim_number VARCHAR(30) NOT NULL,
    policy_id INTEGER NOT NULL,
    claimant_name VARCHAR(100) NOT NULL,
    loss_date DATE NOT NULL,
    reported_date DATE NOT NULL,
    loss_type VARCHAR(40) NOT NULL,
    description VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reserve_amount DOUBLE NOT NULL,
    assigned_adjuster VARCHAR(40) NOT NULL,
    created_by VARCHAR(40) NOT NULL,
    created_date DATE NOT NULL,
    CONSTRAINT pk_claim PRIMARY KEY (claim_id),
    CONSTRAINT uq_claim_number UNIQUE (claim_number),
    CONSTRAINT fk_claim_policy FOREIGN KEY (policy_id) REFERENCES POLICY(policy_id)
);

CREATE TABLE CLAIM_NOTE (
    note_id INTEGER NOT NULL,
    claim_id INTEGER NOT NULL,
    author VARCHAR(40) NOT NULL,
    note_date DATE NOT NULL,
    note_text VARCHAR(500) NOT NULL,
    CONSTRAINT pk_claim_note PRIMARY KEY (note_id),
    CONSTRAINT fk_note_claim FOREIGN KEY (claim_id) REFERENCES CLAIM(claim_id)
);

CREATE TABLE RESERVE_HISTORY (
    history_id INTEGER NOT NULL,
    claim_id INTEGER NOT NULL,
    old_amount DOUBLE NOT NULL,
    new_amount DOUBLE NOT NULL,
    changed_by VARCHAR(40) NOT NULL,
    changed_date DATE NOT NULL,
    reason VARCHAR(200) NOT NULL,
    CONSTRAINT pk_reserve_history PRIMARY KEY (history_id),
    CONSTRAINT fk_history_claim FOREIGN KEY (claim_id) REFERENCES CLAIM(claim_id)
);

CREATE TABLE SETTLEMENT (
    settlement_id INTEGER NOT NULL,
    claim_id INTEGER NOT NULL,
    covered_amount DOUBLE NOT NULL,
    deductible_applied DOUBLE NOT NULL,
    depreciation DOUBLE NOT NULL,
    capped_at_limit BOOLEAN NOT NULL,
    settlement_amount DOUBLE NOT NULL,
    calculated_by VARCHAR(40) NOT NULL,
    calculated_date DATE NOT NULL,
    CONSTRAINT pk_settlement PRIMARY KEY (settlement_id),
    CONSTRAINT fk_settlement_claim FOREIGN KEY (claim_id) REFERENCES CLAIM(claim_id)
);

CREATE TABLE PAYMENT (
    payment_id INTEGER NOT NULL,
    claim_id INTEGER NOT NULL,
    settlement_id INTEGER NOT NULL,
    payee_name VARCHAR(100) NOT NULL,
    amount DOUBLE NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    check_number VARCHAR(30) NOT NULL,
    issued_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_payment PRIMARY KEY (payment_id),
    CONSTRAINT fk_payment_claim FOREIGN KEY (claim_id) REFERENCES CLAIM(claim_id),
    CONSTRAINT fk_payment_settlement FOREIGN KEY (settlement_id) REFERENCES SETTLEMENT(settlement_id)
);

CREATE TABLE ADJUSTER (
    adjuster_id INTEGER NOT NULL,
    username VARCHAR(40) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    region VARCHAR(40) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_adjuster PRIMARY KEY (adjuster_id),
    CONSTRAINT uq_adjuster_username UNIQUE (username)
);

CREATE INDEX ix_policy_line ON POLICY(line_of_business);
CREATE INDEX ix_claim_status ON CLAIM(status);
CREATE INDEX ix_claim_adjuster ON CLAIM(assigned_adjuster);
CREATE INDEX ix_claim_policy ON CLAIM(policy_id);
CREATE INDEX ix_note_claim ON CLAIM_NOTE(claim_id);
CREATE INDEX ix_payment_claim ON PAYMENT(claim_id);
CREATE INDEX ix_history_claim ON RESERVE_HISTORY(claim_id);
