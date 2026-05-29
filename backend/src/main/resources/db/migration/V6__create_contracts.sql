-- Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
-- All rights reserved.

-- Contracts table: links a contractor to a formal service agreement.
CREATE TABLE contracts (
    id                  UUID                NOT NULL DEFAULT gen_random_uuid(),
    contractor_id       UUID                NOT NULL,
    title               VARCHAR(255)        NOT NULL,
    scope               TEXT,
    contract_value      NUMERIC(18,2),
    currency            VARCHAR(3)          NOT NULL DEFAULT 'VND',
    start_date          DATE                NOT NULL,
    end_date            DATE,
    status              contract_status     NOT NULL DEFAULT 'PENDING',
    attachment_url      VARCHAR(1000),
    notes               TEXT,
    created_by_user_id  UUID,
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_contracts             PRIMARY KEY (id),
    CONSTRAINT fk_contracts_contractor  FOREIGN KEY (contractor_id)      REFERENCES contractors(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contracts_creator     FOREIGN KEY (created_by_user_id) REFERENCES users(id)       ON DELETE SET NULL,
    CONSTRAINT chk_contracts_dates      CHECK (end_date IS NULL OR end_date > start_date)
);
CREATE INDEX idx_contracts_contractor_id ON contracts (contractor_id);
CREATE INDEX idx_contracts_status        ON contracts (status);
CREATE INDEX idx_contracts_end_date      ON contracts (end_date) WHERE status = 'ACTIVE';

-- Contract payments: individual disbursement records against a contract.
CREATE TABLE contract_payments (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    contract_id         UUID            NOT NULL,
    amount              NUMERIC(18,2)   NOT NULL CHECK (amount > 0),
    payment_date        DATE            NOT NULL,
    description         TEXT,
    reference_number    VARCHAR(100),
    recorded_by_user_id UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_contract_payments          PRIMARY KEY (id),
    CONSTRAINT fk_cp_contract FOREIGN KEY (contract_id)          REFERENCES contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cp_user     FOREIGN KEY (recorded_by_user_id)  REFERENCES users(id)     ON DELETE SET NULL
);
CREATE INDEX idx_cp_contract_id  ON contract_payments (contract_id);
CREATE INDEX idx_cp_payment_date ON contract_payments (payment_date DESC);

-- Maintenance schedules: recurring maintenance tasks associated with a contract.
CREATE TABLE maintenance_schedules (
    id              UUID                    NOT NULL DEFAULT gen_random_uuid(),
    contract_id     UUID                    NOT NULL,
    title           VARCHAR(255)            NOT NULL,
    frequency       schedule_frequency      NOT NULL,
    next_due_date   DATE                    NOT NULL,
    last_done_date  DATE,
    notes           TEXT,
    is_active       BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_maintenance_schedules          PRIMARY KEY (id),
    CONSTRAINT fk_ms_contract FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE RESTRICT
);
CREATE INDEX idx_ms_contract_id   ON maintenance_schedules (contract_id);
CREATE INDEX idx_ms_next_due_date ON maintenance_schedules (next_due_date);
CREATE INDEX idx_ms_active        ON maintenance_schedules (is_active) WHERE is_active = TRUE;
