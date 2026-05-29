-- Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
-- All rights reserved.

-- Contractors table (needed as FK target for tickets)
CREATE TABLE contractors (
    id              UUID                    NOT NULL DEFAULT gen_random_uuid(),
    company_name    VARCHAR(255)            NOT NULL,
    contact_person  VARCHAR(255),
    phone           VARCHAR(20),
    email           VARCHAR(255),
    address         TEXT,
    specialty       contractor_specialty    NOT NULL DEFAULT 'OTHER',
    tax_code        VARCHAR(50),
    rating          NUMERIC(3,2)            CHECK (rating BETWEEN 0 AND 5),
    notes           TEXT,
    is_active       BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_contractors PRIMARY KEY (id)
);
CREATE INDEX idx_contractors_specialty ON contractors (specialty);
CREATE INDEX idx_contractors_is_active ON contractors (is_active);
CREATE INDEX idx_contractors_rating    ON contractors (rating DESC NULLS LAST);

-- Tickets table
CREATE TABLE tickets (
    id                          UUID                NOT NULL DEFAULT gen_random_uuid(),
    apartment_id                UUID                NOT NULL,
    submitted_by_user_id        UUID                NOT NULL,
    category                    ticket_category     NOT NULL,
    title                       VARCHAR(255)        NOT NULL,
    description                 TEXT,
    status                      ticket_status       NOT NULL DEFAULT 'NEW',
    priority                    ticket_priority     NOT NULL DEFAULT 'MEDIUM',
    assigned_to_user_id         UUID,
    assigned_to_contractor_id   UUID,
    scheduled_date              DATE,
    completed_date              TIMESTAMPTZ,
    rating                      SMALLINT            CHECK (rating BETWEEN 1 AND 5),
    rating_comment              TEXT,
    sla_deadline                TIMESTAMPTZ,
    resolution_notes            TEXT,
    created_at                  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tickets                     PRIMARY KEY (id),
    CONSTRAINT fk_tickets_apartment           FOREIGN KEY (apartment_id)              REFERENCES apartments(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_submitter           FOREIGN KEY (submitted_by_user_id)      REFERENCES users(id)        ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_staff               FOREIGN KEY (assigned_to_user_id)       REFERENCES users(id)        ON DELETE SET NULL,
    CONSTRAINT fk_tickets_contractor          FOREIGN KEY (assigned_to_contractor_id) REFERENCES contractors(id)  ON DELETE SET NULL,
    CONSTRAINT chk_tickets_contractor_category CHECK (
        assigned_to_contractor_id IS NULL OR category = 'MAINTENANCE_REPAIR'
    ),
    CONSTRAINT chk_tickets_single_assignee CHECK (
        NOT (assigned_to_user_id IS NOT NULL AND assigned_to_contractor_id IS NOT NULL)
    )
);
CREATE INDEX idx_tickets_apartment_id              ON tickets (apartment_id);
CREATE INDEX idx_tickets_submitted_by             ON tickets (submitted_by_user_id);
CREATE INDEX idx_tickets_category                 ON tickets (category);
CREATE INDEX idx_tickets_status                   ON tickets (status);
CREATE INDEX idx_tickets_priority                 ON tickets (priority);
CREATE INDEX idx_tickets_assigned_to_user         ON tickets (assigned_to_user_id);
CREATE INDEX idx_tickets_assigned_to_contractor   ON tickets (assigned_to_contractor_id);
CREATE INDEX idx_tickets_created_at               ON tickets (created_at DESC);
CREATE INDEX idx_tickets_sla_deadline             ON tickets (sla_deadline) WHERE status NOT IN ('DONE','CANCELLED');
CREATE INDEX idx_tickets_category_status_created  ON tickets (category, status, created_at DESC);

-- Ticket photos
CREATE TABLE ticket_photos (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    ticket_id   UUID            NOT NULL,
    file_url    VARCHAR(1000)   NOT NULL,
    file_name   VARCHAR(255),
    mime_type   VARCHAR(100),
    file_size   INTEGER,
    phase       photo_phase     NOT NULL DEFAULT 'BEFORE',
    uploaded_by UUID,
    uploaded_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ticket_photos          PRIMARY KEY (id),
    CONSTRAINT fk_ticket_photos_ticket   FOREIGN KEY (ticket_id)    REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_photos_uploader FOREIGN KEY (uploaded_by)  REFERENCES users(id)   ON DELETE SET NULL
);
CREATE INDEX idx_ticket_photos_ticket_id ON ticket_photos (ticket_id);
CREATE INDEX idx_ticket_photos_phase     ON ticket_photos (phase);

-- Ticket status history (append-only)
CREATE TABLE ticket_status_history (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    ticket_id           UUID            NOT NULL,
    old_status          ticket_status,
    new_status          ticket_status   NOT NULL,
    changed_by_user_id  UUID,
    notes               TEXT,
    changed_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ticket_status_history        PRIMARY KEY (id),
    CONSTRAINT fk_tsh_ticket   FOREIGN KEY (ticket_id)          REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_tsh_user     FOREIGN KEY (changed_by_user_id) REFERENCES users(id)   ON DELETE SET NULL
);
CREATE INDEX idx_tsh_ticket_id  ON ticket_status_history (ticket_id);
CREATE INDEX idx_tsh_changed_at ON ticket_status_history (changed_at DESC);
