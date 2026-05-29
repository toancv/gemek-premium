-- =============================================================================
-- Apartment Management System — PostgreSQL 15 Schema
-- Version: 2.0
-- Date: 2026-05-29
-- =============================================================================
-- Conventions:
--   - All PKs are UUID (gen_random_uuid()) — prevents sequential ID enumeration
--   - All timestamps stored as TIMESTAMPTZ (UTC)
--   - Soft deletes NOT used — logical status / is_active fields used instead
--   - PostgreSQL ENUM types for storage efficiency and DB-level constraint
--   - Flyway migrations: V1 = tables, V2 = indexes, V3 = seed data
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- provides gen_random_uuid()

-- ---------------------------------------------------------------------------
-- ENUM Types
-- ---------------------------------------------------------------------------

CREATE TYPE user_role AS ENUM (
    'ADMIN',
    'TECHNICIAN',
    'RESIDENT',
    'BOARD_MEMBER'
);

CREATE TYPE apartment_status AS ENUM (
    'AVAILABLE',
    'OCCUPIED',
    'MAINTENANCE'
);

CREATE TYPE resident_type AS ENUM (
    'OWNER',
    'TENANT'
);

CREATE TYPE vehicle_type AS ENUM (
    'CAR',
    'MOTORBIKE',
    'BICYCLE',
    'OTHER'
);

CREATE TYPE parking_slot_type AS ENUM (
    'CAR',
    'MOTORBIKE',
    'BICYCLE'
);

CREATE TYPE parking_slot_status AS ENUM (
    'AVAILABLE',
    'OCCUPIED',
    'RESERVED'
);

-- ticket_category drives routing and contractor assignment rules.
-- MAINTENANCE_REPAIR is the only category that permits contractor assignment.
-- SUGGESTION_FEEDBACK routes to admin review queue; no explicit assignee.
CREATE TYPE ticket_category AS ENUM (
    'MAINTENANCE_REPAIR',
    'COMPLAINT',
    'ADMINISTRATIVE',
    'SUGGESTION_FEEDBACK',
    'OTHER'
);

CREATE TYPE ticket_status AS ENUM (
    'NEW',
    'ASSIGNED',
    'IN_PROGRESS',
    'DONE',
    'CANCELLED'
);

CREATE TYPE ticket_priority AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH',
    'URGENT'
);

CREATE TYPE photo_phase AS ENUM (
    'BEFORE',
    'PROGRESS',
    'AFTER'
);

CREATE TYPE booking_status AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED',
    'CANCELLED',
    'COMPLETED'
);

CREATE TYPE contractor_specialty AS ENUM (
    'CLEANING',
    'SECURITY',
    'ELEVATOR',
    'FIRE_SAFETY',
    'LANDSCAPING',
    'PEST_CONTROL',
    'ELECTRICAL',
    'PLUMBING',
    'OTHER'
);

CREATE TYPE contract_status AS ENUM (
    'PENDING',
    'ACTIVE',
    'EXPIRED',
    'TERMINATED'
);

CREATE TYPE schedule_frequency AS ENUM (
    'DAILY',
    'WEEKLY',
    'MONTHLY',
    'QUARTERLY',
    'ANNUAL'
);

CREATE TYPE announcement_type AS ENUM (
    'GENERAL',
    'URGENT',
    'MAINTENANCE',
    'AMENITY',
    'EVENT'
);

CREATE TYPE announcement_scope AS ENUM (
    'ALL',
    'BLOCK',
    'FLOOR'
);

CREATE TYPE notification_type AS ENUM (
    'TICKET_ASSIGNED',
    'TICKET_STATUS_CHANGED',
    'TICKET_RATED',
    'TICKET_SLA_BREACHED',
    'BOOKING_APPROVED',
    'BOOKING_REJECTED',
    'BOOKING_REMINDER',
    'ANNOUNCEMENT_PUBLISHED',
    'CONTRACT_EXPIRING',
    'SCHEDULE_DUE',
    'GENERAL'
);

-- ---------------------------------------------------------------------------
-- TABLE: users
-- Central identity table. All roles use this table.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    email               VARCHAR(255)    NOT NULL,
    phone               VARCHAR(20),
    full_name           VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    role                user_role       NOT NULL DEFAULT 'RESIDENT',
    fcm_token           VARCHAR(500),               -- Firebase Cloud Messaging device token
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    avatar_url          VARCHAR(1000),              -- MinIO object key, not full URL
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_role        ON users (role);
CREATE INDEX idx_users_is_active   ON users (is_active);
CREATE INDEX idx_users_phone       ON users (phone);

-- ---------------------------------------------------------------------------
-- TABLE: blocks
-- Building blocks or towers (e.g., Block A, Block B)
-- ---------------------------------------------------------------------------
CREATE TABLE blocks (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100)    NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_blocks        PRIMARY KEY (id),
    CONSTRAINT uq_blocks_name   UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- TABLE: apartments
-- Individual apartment units within a block
-- ---------------------------------------------------------------------------
CREATE TABLE apartments (
    id              UUID                NOT NULL DEFAULT gen_random_uuid(),
    block_id        UUID                NOT NULL,
    floor           SMALLINT            NOT NULL CHECK (floor >= 0),
    unit_number     VARCHAR(20)         NOT NULL,
    area_sqm        NUMERIC(8, 2)       CHECK (area_sqm > 0),
    status          apartment_status    NOT NULL DEFAULT 'AVAILABLE',
    notes           TEXT,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_apartments            PRIMARY KEY (id),
    CONSTRAINT fk_apartments_block      FOREIGN KEY (block_id) REFERENCES blocks (id) ON DELETE RESTRICT,
    CONSTRAINT uq_apartments_block_unit UNIQUE (block_id, unit_number)
);

CREATE INDEX idx_apartments_block_id ON apartments (block_id);
CREATE INDEX idx_apartments_floor    ON apartments (floor);
CREATE INDEX idx_apartments_status   ON apartments (status);

-- ---------------------------------------------------------------------------
-- TABLE: residents
-- Links a user to an apartment with role type and date range.
-- An apartment may have multiple residents (one owner + one or more tenants).
-- A user may be an active resident of at most one apartment at a time
--   (enforced by the partial unique index below).
-- ---------------------------------------------------------------------------
CREATE TABLE residents (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    apartment_id        UUID            NOT NULL,
    type                resident_type   NOT NULL,
    move_in_date        DATE            NOT NULL,
    move_out_date       DATE,                           -- NULL = currently active
    is_primary_contact  BOOLEAN         NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_residents             PRIMARY KEY (id),
    CONSTRAINT fk_residents_user        FOREIGN KEY (user_id)       REFERENCES users (id)       ON DELETE RESTRICT,
    CONSTRAINT fk_residents_apartment   FOREIGN KEY (apartment_id)  REFERENCES apartments (id)  ON DELETE RESTRICT
);

CREATE INDEX idx_residents_user_id      ON residents (user_id);
CREATE INDEX idx_residents_apartment_id ON residents (apartment_id);
CREATE INDEX idx_residents_type         ON residents (type);
-- Partial unique index: a user may only be active in one apartment at a time
CREATE UNIQUE INDEX uq_residents_active_user ON residents (user_id) WHERE move_out_date IS NULL;
-- Partial index for current active residents of an apartment
CREATE INDEX idx_residents_active       ON residents (apartment_id) WHERE move_out_date IS NULL;

-- ---------------------------------------------------------------------------
-- TABLE: resident_history
-- Immutable log of all resident assignment events for an apartment.
-- Written by service layer on every resident create / update / move-out event.
-- ---------------------------------------------------------------------------
CREATE TABLE resident_history (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    apartment_id        UUID            NOT NULL,
    user_id             UUID            NOT NULL,
    type                resident_type   NOT NULL,
    event               VARCHAR(50)     NOT NULL,       -- 'MOVED_IN', 'MOVED_OUT', 'TYPE_CHANGED'
    event_date          DATE            NOT NULL,
    changed_by_user_id  UUID,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_resident_history              PRIMARY KEY (id),
    CONSTRAINT fk_resident_history_apartment    FOREIGN KEY (apartment_id)       REFERENCES apartments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_resident_history_user         FOREIGN KEY (user_id)            REFERENCES users (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_resident_history_changed_by   FOREIGN KEY (changed_by_user_id) REFERENCES users (id)      ON DELETE SET NULL
);

CREATE INDEX idx_resident_history_apartment_id ON resident_history (apartment_id);
CREATE INDEX idx_resident_history_user_id      ON resident_history (user_id);
CREATE INDEX idx_resident_history_event_date   ON resident_history (event_date DESC);

-- ---------------------------------------------------------------------------
-- TABLE: vehicles
-- Vehicles registered by residents
-- ---------------------------------------------------------------------------
CREATE TABLE vehicles (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    resident_id     UUID            NOT NULL,
    apartment_id    UUID            NOT NULL,
    type            vehicle_type    NOT NULL,
    license_plate   VARCHAR(20)     NOT NULL,
    brand           VARCHAR(100),
    model           VARCHAR(100),
    color           VARCHAR(50),
    notes           TEXT,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_vehicles              PRIMARY KEY (id),
    CONSTRAINT fk_vehicles_resident     FOREIGN KEY (resident_id)   REFERENCES residents (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_vehicles_apartment    FOREIGN KEY (apartment_id)  REFERENCES apartments (id)  ON DELETE RESTRICT,
    CONSTRAINT uq_vehicles_license_plate UNIQUE (license_plate)
);

CREATE INDEX idx_vehicles_resident_id   ON vehicles (resident_id);
CREATE INDEX idx_vehicles_apartment_id  ON vehicles (apartment_id);
CREATE INDEX idx_vehicles_type          ON vehicles (type);
CREATE INDEX idx_vehicles_license_plate ON vehicles (license_plate);

-- ---------------------------------------------------------------------------
-- TABLE: parking_slots
-- Physical parking spaces in the building
-- ---------------------------------------------------------------------------
CREATE TABLE parking_slots (
    id              UUID                    NOT NULL DEFAULT gen_random_uuid(),
    slot_number     VARCHAR(20)             NOT NULL,
    zone            VARCHAR(50),                            -- e.g., 'B1', 'B2', 'Ground Floor'
    type            parking_slot_type       NOT NULL,
    status          parking_slot_status     NOT NULL DEFAULT 'AVAILABLE',
    notes           TEXT,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_parking_slots         PRIMARY KEY (id),
    CONSTRAINT uq_parking_slots_number  UNIQUE (slot_number)
);

CREATE INDEX idx_parking_slots_type   ON parking_slots (type);
CREATE INDEX idx_parking_slots_status ON parking_slots (status);
CREATE INDEX idx_parking_slots_zone   ON parking_slots (zone);

-- ---------------------------------------------------------------------------
-- TABLE: parking_assignments
-- Maps a vehicle to a parking slot with a date range and card number
-- ---------------------------------------------------------------------------
CREATE TABLE parking_assignments (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    parking_slot_id     UUID        NOT NULL,
    vehicle_id          UUID        NOT NULL,
    apartment_id        UUID        NOT NULL,
    start_date          DATE        NOT NULL,
    end_date            DATE,                               -- NULL = currently active
    parking_card_number VARCHAR(50),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_parking_assignments           PRIMARY KEY (id),
    CONSTRAINT fk_parking_assignments_slot      FOREIGN KEY (parking_slot_id) REFERENCES parking_slots  (id) ON DELETE RESTRICT,
    CONSTRAINT fk_parking_assignments_vehicle   FOREIGN KEY (vehicle_id)      REFERENCES vehicles        (id) ON DELETE RESTRICT,
    CONSTRAINT fk_parking_assignments_apartment FOREIGN KEY (apartment_id)    REFERENCES apartments      (id) ON DELETE RESTRICT
);

CREATE INDEX idx_parking_assignments_slot_id      ON parking_assignments (parking_slot_id);
CREATE INDEX idx_parking_assignments_vehicle_id   ON parking_assignments (vehicle_id);
CREATE INDEX idx_parking_assignments_apartment_id ON parking_assignments (apartment_id);
-- Partial index for currently active assignments — used to check slot occupancy
CREATE INDEX idx_parking_assignments_active ON parking_assignments (parking_slot_id) WHERE end_date IS NULL;

-- ---------------------------------------------------------------------------
-- TABLE: guest_vehicles
-- Temporary visitor vehicle log
-- ---------------------------------------------------------------------------
CREATE TABLE guest_vehicles (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    license_plate       VARCHAR(20)     NOT NULL,
    owner_name          VARCHAR(255),
    host_apartment_id   UUID            NOT NULL,
    entry_time          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    exit_time           TIMESTAMPTZ,
    purpose             VARCHAR(255),
    recorded_by_user_id UUID,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_guest_vehicles            PRIMARY KEY (id),
    CONSTRAINT fk_guest_vehicles_apartment  FOREIGN KEY (host_apartment_id)    REFERENCES apartments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_guest_vehicles_user       FOREIGN KEY (recorded_by_user_id)  REFERENCES users      (id) ON DELETE SET NULL
);

CREATE INDEX idx_guest_vehicles_apartment_id ON guest_vehicles (host_apartment_id);
CREATE INDEX idx_guest_vehicles_license_plate ON guest_vehicles (license_plate);
CREATE INDEX idx_guest_vehicles_entry_time    ON guest_vehicles (entry_time DESC);

-- ---------------------------------------------------------------------------
-- TABLE: contractors
-- External service vendors
-- ---------------------------------------------------------------------------
CREATE TABLE contractors (
    id              UUID                    NOT NULL DEFAULT gen_random_uuid(),
    company_name    VARCHAR(255)            NOT NULL,
    contact_person  VARCHAR(255),
    phone           VARCHAR(20),
    email           VARCHAR(255),
    address         TEXT,
    specialty       contractor_specialty    NOT NULL DEFAULT 'OTHER',
    tax_code        VARCHAR(50),
    rating          NUMERIC(3, 2)           CHECK (rating BETWEEN 0 AND 5),
    notes           TEXT,
    is_active       BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_contractors PRIMARY KEY (id)
);

CREATE INDEX idx_contractors_specialty ON contractors (specialty);
CREATE INDEX idx_contractors_is_active ON contractors (is_active);
CREATE INDEX idx_contractors_rating    ON contractors (rating DESC NULLS LAST);

-- ---------------------------------------------------------------------------
-- TABLE: tickets
-- Core resident request and ticket tracking table.
-- Replaces the old maintenance_requests table.
--
-- Category routing rules enforced by CHECK constraint and service layer:
--   MAINTENANCE_REPAIR  → staff OR contractor
--   COMPLAINT           → staff only
--   ADMINISTRATIVE      → admin/staff only
--   SUGGESTION_FEEDBACK → admin review queue (no explicit assignee)
--   OTHER               → staff only
--
-- The constraint below enforces that contractor assignment is only valid
-- when category = 'MAINTENANCE_REPAIR'.
-- ---------------------------------------------------------------------------
CREATE TABLE tickets (
    id                          UUID                NOT NULL DEFAULT gen_random_uuid(),
    apartment_id                UUID                NOT NULL,
    submitted_by_user_id        UUID                NOT NULL,
    category                    ticket_category     NOT NULL,
    title                       VARCHAR(255)        NOT NULL,
    description                 TEXT,
    status                      ticket_status       NOT NULL DEFAULT 'NEW',
    priority                    ticket_priority     NOT NULL DEFAULT 'MEDIUM',
    assigned_to_user_id         UUID,               -- internal staff (TECHNICIAN or ADMIN)
    assigned_to_contractor_id   UUID,               -- external contractor (MAINTENANCE_REPAIR only)
    scheduled_date              DATE,
    completed_date              TIMESTAMPTZ,
    rating                      SMALLINT            CHECK (rating BETWEEN 1 AND 5),
    rating_comment              TEXT,
    sla_deadline                TIMESTAMPTZ,        -- computed at INSERT: created_at + category SLA
    resolution_notes            TEXT,
    created_at                  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tickets                       PRIMARY KEY (id),
    CONSTRAINT fk_tickets_apartment             FOREIGN KEY (apartment_id)              REFERENCES apartments   (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_submitter             FOREIGN KEY (submitted_by_user_id)      REFERENCES users        (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_staff                 FOREIGN KEY (assigned_to_user_id)       REFERENCES users        (id) ON DELETE SET NULL,
    CONSTRAINT fk_tickets_contractor            FOREIGN KEY (assigned_to_contractor_id) REFERENCES contractors  (id) ON DELETE SET NULL,

    -- Contractor assignment is only valid for MAINTENANCE_REPAIR category
    CONSTRAINT chk_tickets_contractor_category CHECK (
        assigned_to_contractor_id IS NULL OR category = 'MAINTENANCE_REPAIR'
    ),

    -- Only one assignee (staff or contractor) at a time
    CONSTRAINT chk_tickets_single_assignee CHECK (
        NOT (assigned_to_user_id IS NOT NULL AND assigned_to_contractor_id IS NOT NULL)
    )
);

CREATE INDEX idx_tickets_apartment_id           ON tickets (apartment_id);
CREATE INDEX idx_tickets_submitted_by           ON tickets (submitted_by_user_id);
CREATE INDEX idx_tickets_category               ON tickets (category);
CREATE INDEX idx_tickets_status                 ON tickets (status);
CREATE INDEX idx_tickets_priority               ON tickets (priority);
CREATE INDEX idx_tickets_assigned_to_user       ON tickets (assigned_to_user_id);
CREATE INDEX idx_tickets_assigned_to_contractor ON tickets (assigned_to_contractor_id);
CREATE INDEX idx_tickets_created_at             ON tickets (created_at DESC);
-- Partial index for SLA scheduler: only non-terminal tickets with a deadline
CREATE INDEX idx_tickets_sla_deadline ON tickets (sla_deadline)
    WHERE status NOT IN ('DONE', 'CANCELLED');
-- Composite index for common admin list view: category + status + date range
CREATE INDEX idx_tickets_category_status_created ON tickets (category, status, created_at DESC);

-- ---------------------------------------------------------------------------
-- TABLE: ticket_photos
-- Photos attached to a ticket (before submission, during work, after completion)
-- ---------------------------------------------------------------------------
CREATE TABLE ticket_photos (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    ticket_id   UUID            NOT NULL,
    file_url    VARCHAR(1000)   NOT NULL,    -- MinIO object key (not full URL; presigned at fetch time)
    file_name   VARCHAR(255),
    mime_type   VARCHAR(100),
    file_size   INTEGER,                     -- bytes
    phase       photo_phase     NOT NULL DEFAULT 'BEFORE',
    uploaded_by UUID,
    uploaded_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ticket_photos         PRIMARY KEY (id),
    CONSTRAINT fk_ticket_photos_ticket  FOREIGN KEY (ticket_id)    REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_photos_uploader FOREIGN KEY (uploaded_by) REFERENCES users   (id) ON DELETE SET NULL
);

CREATE INDEX idx_ticket_photos_ticket_id ON ticket_photos (ticket_id);
CREATE INDEX idx_ticket_photos_phase     ON ticket_photos (phase);

-- ---------------------------------------------------------------------------
-- TABLE: ticket_status_history
-- Immutable log of every status transition for a ticket.
-- Written on every status change — never updated or deleted.
-- ---------------------------------------------------------------------------
CREATE TABLE ticket_status_history (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    ticket_id           UUID            NOT NULL,
    old_status          ticket_status,              -- NULL on initial creation
    new_status          ticket_status   NOT NULL,
    changed_by_user_id  UUID,
    notes               TEXT,
    changed_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ticket_status_history         PRIMARY KEY (id),
    CONSTRAINT fk_ticket_status_history_ticket  FOREIGN KEY (ticket_id)           REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_status_history_user    FOREIGN KEY (changed_by_user_id)  REFERENCES users   (id) ON DELETE SET NULL
);

CREATE INDEX idx_ticket_status_history_ticket_id  ON ticket_status_history (ticket_id);
CREATE INDEX idx_ticket_status_history_changed_at ON ticket_status_history (changed_at DESC);

-- ---------------------------------------------------------------------------
-- TABLE: amenities
-- Bookable building facilities (gym, pool, BBQ area, meeting room, etc.)
-- ---------------------------------------------------------------------------
CREATE TABLE amenities (
    id                                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    name                                VARCHAR(100)    NOT NULL,
    description                         TEXT,
    location                            VARCHAR(255),
    capacity                            SMALLINT        CHECK (capacity > 0),
    opening_time                        TIME            NOT NULL DEFAULT '06:00',
    closing_time                        TIME            NOT NULL DEFAULT '22:00',
    max_daily_bookings_per_resident     SMALLINT        NOT NULL DEFAULT 1 CHECK (max_daily_bookings_per_resident > 0),
    requires_approval                   BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active                           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_amenities         PRIMARY KEY (id),
    CONSTRAINT uq_amenities_name    UNIQUE (name),
    CONSTRAINT chk_amenities_hours  CHECK (opening_time < closing_time)
);

-- ---------------------------------------------------------------------------
-- TABLE: amenity_bookings
-- Resident reservations for building amenities
-- ---------------------------------------------------------------------------
CREATE TABLE amenity_bookings (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    amenity_id          UUID            NOT NULL,
    resident_id         UUID            NOT NULL,       -- references residents.id (not users.id)
    apartment_id        UUID            NOT NULL,
    booking_date        DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME            NOT NULL,
    status              booking_status  NOT NULL DEFAULT 'PENDING',
    notes               TEXT,
    rejection_reason    TEXT,
    approved_by_user_id UUID,
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_amenity_bookings              PRIMARY KEY (id),
    CONSTRAINT fk_amenity_bookings_amenity      FOREIGN KEY (amenity_id)            REFERENCES amenities    (id) ON DELETE RESTRICT,
    CONSTRAINT fk_amenity_bookings_resident     FOREIGN KEY (resident_id)           REFERENCES residents    (id) ON DELETE RESTRICT,
    CONSTRAINT fk_amenity_bookings_apartment    FOREIGN KEY (apartment_id)          REFERENCES apartments   (id) ON DELETE RESTRICT,
    CONSTRAINT fk_amenity_bookings_approver     FOREIGN KEY (approved_by_user_id)   REFERENCES users        (id) ON DELETE SET NULL,
    CONSTRAINT chk_amenity_bookings_times       CHECK (start_time < end_time)
);

CREATE INDEX idx_amenity_bookings_amenity_id   ON amenity_bookings (amenity_id);
CREATE INDEX idx_amenity_bookings_resident_id  ON amenity_bookings (resident_id);
CREATE INDEX idx_amenity_bookings_apartment_id ON amenity_bookings (apartment_id);
CREATE INDEX idx_amenity_bookings_status       ON amenity_bookings (status);
-- Composite index for availability / conflict check (amenity + date + status)
CREATE INDEX idx_amenity_bookings_date_amenity ON amenity_bookings (amenity_id, booking_date, status);

-- ---------------------------------------------------------------------------
-- TABLE: contracts
-- Service contracts with vendors
-- ---------------------------------------------------------------------------
CREATE TABLE contracts (
    id                  UUID                NOT NULL DEFAULT gen_random_uuid(),
    contractor_id       UUID                NOT NULL,
    title               VARCHAR(255)        NOT NULL,
    scope               TEXT,
    contract_value      NUMERIC(18, 2),
    currency            VARCHAR(3)          NOT NULL DEFAULT 'VND',
    start_date          DATE                NOT NULL,
    end_date            DATE,
    status              contract_status     NOT NULL DEFAULT 'PENDING',
    attachment_url      VARCHAR(1000),               -- MinIO object key for contract PDF
    notes               TEXT,
    created_by_user_id  UUID,
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_contracts             PRIMARY KEY (id),
    CONSTRAINT fk_contracts_contractor  FOREIGN KEY (contractor_id)       REFERENCES contractors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_contracts_creator     FOREIGN KEY (created_by_user_id)  REFERENCES users       (id) ON DELETE SET NULL,
    CONSTRAINT chk_contracts_dates      CHECK (end_date IS NULL OR end_date > start_date)
);

CREATE INDEX idx_contracts_contractor_id ON contracts (contractor_id);
CREATE INDEX idx_contracts_status        ON contracts (status);
-- Partial index for expiry alert scheduler: only active contracts with an end date
CREATE INDEX idx_contracts_end_date ON contracts (end_date) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- TABLE: contract_payments
-- Payment records against a contract.
-- Record and remind only — this is NOT a disbursement-approval workflow.
-- ---------------------------------------------------------------------------
CREATE TABLE contract_payments (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    contract_id         UUID            NOT NULL,
    amount              NUMERIC(18, 2)  NOT NULL CHECK (amount > 0),
    payment_date        DATE            NOT NULL,
    description         TEXT,
    reference_number    VARCHAR(100),               -- bank transfer reference or invoice number
    recorded_by_user_id UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_contract_payments         PRIMARY KEY (id),
    CONSTRAINT fk_contract_payments_contract FOREIGN KEY (contract_id)          REFERENCES contracts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_payments_user    FOREIGN KEY (recorded_by_user_id)   REFERENCES users     (id) ON DELETE SET NULL
);

CREATE INDEX idx_contract_payments_contract_id  ON contract_payments (contract_id);
CREATE INDEX idx_contract_payments_payment_date ON contract_payments (payment_date DESC);

-- ---------------------------------------------------------------------------
-- TABLE: maintenance_schedules
-- Recurring maintenance tasks tied to a service contract.
-- ---------------------------------------------------------------------------
CREATE TABLE maintenance_schedules (
    id              UUID                NOT NULL DEFAULT gen_random_uuid(),
    contract_id     UUID                NOT NULL,
    title           VARCHAR(255)        NOT NULL,
    description     TEXT,
    frequency       schedule_frequency  NOT NULL,
    next_due_date   DATE                NOT NULL,
    last_done_date  DATE,
    is_active       BOOLEAN             NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_maintenance_schedules         PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_schedules_contract FOREIGN KEY (contract_id) REFERENCES contracts (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_schedules_contract_id ON maintenance_schedules (contract_id);
-- Partial index for the daily scheduler: only active schedules with upcoming due dates
CREATE INDEX idx_maintenance_schedules_next_due ON maintenance_schedules (next_due_date) WHERE is_active = TRUE;

-- ---------------------------------------------------------------------------
-- TABLE: announcements
-- Building-wide or targeted notices.
-- A draft has published_at = NULL; publishing sets published_at = NOW().
-- ---------------------------------------------------------------------------
CREATE TABLE announcements (
    id                  UUID                    NOT NULL DEFAULT gen_random_uuid(),
    title               VARCHAR(255)            NOT NULL,
    content             TEXT                    NOT NULL,
    type                announcement_type       NOT NULL DEFAULT 'GENERAL',
    target_scope        announcement_scope      NOT NULL DEFAULT 'ALL',
    target_block_id     UUID,                               -- required when scope = BLOCK or FLOOR
    target_floor        SMALLINT,                           -- required when scope = FLOOR
    send_push           BOOLEAN                 NOT NULL DEFAULT TRUE,
    send_email          BOOLEAN                 NOT NULL DEFAULT FALSE,
    send_sms            BOOLEAN                 NOT NULL DEFAULT FALSE,
    attachment_url      VARCHAR(1000),                      -- MinIO object key for optional attachment
    created_by_user_id  UUID                    NOT NULL,
    published_at        TIMESTAMPTZ,                        -- NULL = draft
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_announcements         PRIMARY KEY (id),
    CONSTRAINT fk_announcements_block   FOREIGN KEY (target_block_id)    REFERENCES blocks (id) ON DELETE SET NULL,
    CONSTRAINT fk_announcements_creator FOREIGN KEY (created_by_user_id) REFERENCES users  (id) ON DELETE RESTRICT,
    -- Enforce that target_block_id and target_floor are consistent with target_scope
    CONSTRAINT chk_announcements_scope CHECK (
        (target_scope = 'ALL')
        OR (target_scope = 'BLOCK' AND target_block_id IS NOT NULL)
        OR (target_scope = 'FLOOR' AND target_block_id IS NOT NULL AND target_floor IS NOT NULL)
    )
);

CREATE INDEX idx_announcements_type         ON announcements (type);
CREATE INDEX idx_announcements_scope        ON announcements (target_scope);
CREATE INDEX idx_announcements_block_id     ON announcements (target_block_id);
CREATE INDEX idx_announcements_created_by   ON announcements (created_by_user_id);
-- Partial index for published announcements used in resident feeds
CREATE INDEX idx_announcements_published_at ON announcements (published_at DESC) WHERE published_at IS NOT NULL;

-- ---------------------------------------------------------------------------
-- TABLE: announcement_reads
-- Read receipts — one row per user per announcement.
-- ---------------------------------------------------------------------------
CREATE TABLE announcement_reads (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    announcement_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    read_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_announcement_reads            PRIMARY KEY (id),
    CONSTRAINT fk_announcement_reads_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_reads_user       FOREIGN KEY (user_id)           REFERENCES users         (id) ON DELETE CASCADE,
    CONSTRAINT uq_announcement_reads            UNIQUE (announcement_id, user_id)
);

CREATE INDEX idx_announcement_reads_user_id         ON announcement_reads (user_id);
CREATE INDEX idx_announcement_reads_announcement_id ON announcement_reads (announcement_id);

-- ---------------------------------------------------------------------------
-- TABLE: notifications
-- In-app notification inbox for each user.
-- Also serves as the delivery audit trail — a record is created regardless
-- of whether FCM / email / SMS delivery succeeded.
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id              UUID                NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID                NOT NULL,
    title           VARCHAR(255)        NOT NULL,
    body            TEXT,
    type            notification_type   NOT NULL DEFAULT 'GENERAL',
    reference_id    UUID,               -- ID of the referenced entity (ticket, booking, etc.)
    reference_type  VARCHAR(100),       -- entity class name (e.g., 'Ticket', 'AmenityBooking')
    is_read         BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notifications         PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user_id    ON notifications (user_id);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
CREATE INDEX idx_notifications_type       ON notifications (type);
-- Partial index for unread badge count queries
CREATE INDEX idx_notifications_unread ON notifications (user_id, is_read) WHERE is_read = FALSE;

-- ---------------------------------------------------------------------------
-- TABLE: audit_logs
-- Immutable audit trail for all significant system actions.
-- No FK on user_id intentionally — audit log must survive user deletion.
-- Never UPDATE or DELETE rows in this table.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID,                               -- NULL for system-triggered actions
    action          VARCHAR(100)    NOT NULL,           -- 'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'STATUS_CHANGE'
    entity_type     VARCHAR(100)    NOT NULL,           -- 'Ticket', 'Resident', 'AmenityBooking', etc.
    entity_id       UUID,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

CREATE INDEX idx_audit_logs_user_id    ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_entity     ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_action     ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
-- GIN index for searching serialized changed values
CREATE INDEX idx_audit_logs_new_value_gin ON audit_logs USING GIN (new_value);

-- =============================================================================
-- SEED DATA
-- Reference / lookup data required for initial system operation.
-- =============================================================================

-- Default admin user
-- Email: admin@gemek.vn
-- Password: Admin@123456 (BCrypt hash, strength 12)
-- IMPORTANT: This password MUST be changed immediately after first login.
-- The hash below is a placeholder — replace with a real BCrypt hash generated
-- at deployment time. Never commit a real password hash to source control.
INSERT INTO users (email, phone, full_name, password_hash, role, is_active)
VALUES (
    'admin@gemek.vn',
    '0900000000',
    'System Administrator',
    '$2a$12$PLACEHOLDER_CHANGE_BEFORE_DEPLOY_xxxxxxxxxxxxxxxxxxxxxxxxxxx',
    'ADMIN',
    TRUE
);

-- Default amenities (common for Vietnamese apartment complexes)
INSERT INTO amenities (name, description, location, capacity, opening_time, closing_time, max_daily_bookings_per_resident, requires_approval, is_active)
VALUES
    ('Gym / Fitness Center',  'Fully equipped fitness center', 'Floor 2', 20, '05:30', '22:00', 1, FALSE, TRUE),
    ('Swimming Pool',         'Outdoor rooftop pool',          'Rooftop', 30, '06:00', '21:00', 1, FALSE, TRUE),
    ('BBQ Area',              'Outdoor BBQ and dining area',   'Rooftop', 20, '10:00', '22:00', 1, TRUE,  TRUE),
    ('Meeting Room A',        'Conference room, 10 seats',     'Floor 1', 10, '08:00', '21:00', 2, TRUE,  TRUE),
    ('Meeting Room B',        'Conference room, 6 seats',      'Floor 1', 6,  '08:00', '21:00', 2, TRUE,  TRUE),
    ('Kids Playground',       'Outdoor playground area',       'Ground',  NULL,'07:00', '20:00', 0, FALSE, TRUE);
-- Note: max_daily_bookings_per_resident = 0 on Kids Playground means no booking required (walk-in).

-- =============================================================================
-- NOTES FOR FLYWAY MIGRATION SPLIT
-- =============================================================================
-- V1__enums_and_tables.sql  — All CREATE EXTENSION, CREATE TYPE, CREATE TABLE statements
-- V2__indexes.sql            — All CREATE INDEX statements
-- V3__seed_data.sql          — All INSERT statements
--
-- The split allows faster re-running of indexes independently and keeps
-- migration scripts focused. Never edit a V-versioned script after it has
-- been applied to any environment.
-- =============================================================================
