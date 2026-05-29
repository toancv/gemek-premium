-- =============================================================================
-- Apartment Management System — PostgreSQL 15 Schema
-- Version: 1.0
-- Date: 2026-05-29
-- =============================================================================
-- Conventions:
--   - All PKs are UUID (gen_random_uuid()) for security (no sequential ID guessing)
--   - All timestamps stored as TIMESTAMPTZ (UTC)
--   - Soft deletes NOT used — logical status fields used instead (is_active, status enum)
--   - ENUMs defined as PostgreSQL ENUM types for storage efficiency and constraint
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()

-- ---------------------------------------------------------------------------
-- ENUM Types
-- ---------------------------------------------------------------------------

CREATE TYPE user_role AS ENUM ('ADMIN', 'TECHNICIAN', 'RESIDENT', 'BOARD_MEMBER');

CREATE TYPE apartment_status AS ENUM ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE');

CREATE TYPE resident_type AS ENUM ('OWNER', 'TENANT');

CREATE TYPE vehicle_type AS ENUM ('CAR', 'MOTORBIKE', 'BICYCLE', 'OTHER');

CREATE TYPE parking_slot_type AS ENUM ('CAR', 'MOTORBIKE', 'BICYCLE');

CREATE TYPE parking_slot_status AS ENUM ('AVAILABLE', 'OCCUPIED', 'RESERVED');

CREATE TYPE maintenance_status AS ENUM ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'DONE', 'CANCELLED');

CREATE TYPE maintenance_priority AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'URGENT');

CREATE TYPE photo_phase AS ENUM ('BEFORE', 'AFTER', 'PROGRESS');

CREATE TYPE booking_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED');

CREATE TYPE contractor_specialty AS ENUM (
    'CLEANING', 'SECURITY', 'ELEVATOR', 'FIRE_SAFETY',
    'LANDSCAPING', 'PEST_CONTROL', 'ELECTRICAL', 'PLUMBING', 'OTHER'
);

CREATE TYPE contract_status AS ENUM ('PENDING', 'ACTIVE', 'EXPIRED', 'TERMINATED');

CREATE TYPE schedule_frequency AS ENUM ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUAL');

CREATE TYPE announcement_type AS ENUM ('GENERAL', 'URGENT', 'MAINTENANCE', 'AMENITY', 'EVENT');

CREATE TYPE announcement_scope AS ENUM ('ALL', 'BLOCK', 'FLOOR');

CREATE TYPE notification_type AS ENUM (
    'MAINTENANCE_ASSIGNED', 'MAINTENANCE_STATUS_CHANGED', 'MAINTENANCE_RATED',
    'BOOKING_APPROVED', 'BOOKING_REJECTED', 'BOOKING_REMINDER',
    'ANNOUNCEMENT_PUBLISHED', 'CONTRACT_EXPIRING', 'SLA_BREACHED',
    'SCHEDULE_DUE', 'GENERAL'
);

-- ---------------------------------------------------------------------------
-- TABLE: users
-- Central identity table. All roles stored here.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    email               VARCHAR(255)    NOT NULL,
    phone               VARCHAR(20),
    full_name           VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    role                user_role       NOT NULL DEFAULT 'RESIDENT',
    fcm_token           VARCHAR(500),       -- Firebase Cloud Messaging device token
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    avatar_url          VARCHAR(500),
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_is_active ON users (is_active);
CREATE INDEX idx_users_phone ON users (phone);

-- ---------------------------------------------------------------------------
-- TABLE: blocks
-- Building blocks / towers (e.g., Block A, Block B)
-- ---------------------------------------------------------------------------
CREATE TABLE blocks (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100)    NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_blocks PRIMARY KEY (id),
    CONSTRAINT uq_blocks_name UNIQUE (name)
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

    CONSTRAINT pk_apartments PRIMARY KEY (id),
    CONSTRAINT fk_apartments_block FOREIGN KEY (block_id) REFERENCES blocks (id) ON DELETE RESTRICT,
    CONSTRAINT uq_apartments_block_unit UNIQUE (block_id, unit_number)
);

CREATE INDEX idx_apartments_block_id ON apartments (block_id);
CREATE INDEX idx_apartments_floor ON apartments (floor);
CREATE INDEX idx_apartments_status ON apartments (status);

-- ---------------------------------------------------------------------------
-- TABLE: residents
-- Links a user to an apartment with type (OWNER/TENANT) and date range.
-- A single user can be linked to at most one active apartment at a time.
-- An apartment can have multiple residents (owner + tenants).
-- ---------------------------------------------------------------------------
CREATE TABLE residents (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    apartment_id        UUID            NOT NULL,
    type                resident_type   NOT NULL,
    move_in_date        DATE            NOT NULL,
    move_out_date       DATE,                       -- NULL means currently active
    is_primary_contact  BOOLEAN         NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_residents PRIMARY KEY (id),
    CONSTRAINT fk_residents_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_residents_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT,
    -- A user can only be an active resident of one apartment at a time
    CONSTRAINT uq_residents_active_user UNIQUE (user_id, move_out_date)
);

CREATE INDEX idx_residents_user_id ON residents (user_id);
CREATE INDEX idx_residents_apartment_id ON residents (apartment_id);
CREATE INDEX idx_residents_type ON residents (type);
-- Partial index for current residents (no move_out_date set)
CREATE INDEX idx_residents_active ON residents (apartment_id) WHERE move_out_date IS NULL;

-- ---------------------------------------------------------------------------
-- TABLE: resident_history
-- Immutable log of all resident assignment changes for an apartment.
-- Written by service layer on every resident create/update/remove event.
-- ---------------------------------------------------------------------------
CREATE TABLE resident_history (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    apartment_id    UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    type            resident_type   NOT NULL,
    event           VARCHAR(50)     NOT NULL,   -- 'MOVED_IN', 'MOVED_OUT', 'TYPE_CHANGED'
    event_date      DATE            NOT NULL,
    changed_by_user_id UUID,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_resident_history PRIMARY KEY (id),
    CONSTRAINT fk_resident_history_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_resident_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_resident_history_apartment_id ON resident_history (apartment_id);
CREATE INDEX idx_resident_history_user_id ON resident_history (user_id);
CREATE INDEX idx_resident_history_event_date ON resident_history (event_date DESC);

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

    CONSTRAINT pk_vehicles PRIMARY KEY (id),
    CONSTRAINT fk_vehicles_resident FOREIGN KEY (resident_id) REFERENCES residents (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicles_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT,
    CONSTRAINT uq_vehicles_license_plate UNIQUE (license_plate)
);

CREATE INDEX idx_vehicles_resident_id ON vehicles (resident_id);
CREATE INDEX idx_vehicles_apartment_id ON vehicles (apartment_id);
CREATE INDEX idx_vehicles_type ON vehicles (type);
CREATE INDEX idx_vehicles_license_plate ON vehicles (license_plate);

-- ---------------------------------------------------------------------------
-- TABLE: parking_slots
-- Physical parking spaces in the building
-- ---------------------------------------------------------------------------
CREATE TABLE parking_slots (
    id              UUID                    NOT NULL DEFAULT gen_random_uuid(),
    slot_number     VARCHAR(20)             NOT NULL,
    zone            VARCHAR(50),                        -- e.g., 'B1', 'B2', 'Ground'
    type            parking_slot_type       NOT NULL,
    status          parking_slot_status     NOT NULL DEFAULT 'AVAILABLE',
    notes           TEXT,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_parking_slots PRIMARY KEY (id),
    CONSTRAINT uq_parking_slots_number UNIQUE (slot_number)
);

CREATE INDEX idx_parking_slots_type ON parking_slots (type);
CREATE INDEX idx_parking_slots_status ON parking_slots (status);
CREATE INDEX idx_parking_slots_zone ON parking_slots (zone);

-- ---------------------------------------------------------------------------
-- TABLE: parking_assignments
-- Maps a vehicle to a parking slot with date range and card number
-- ---------------------------------------------------------------------------
CREATE TABLE parking_assignments (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    parking_slot_id     UUID        NOT NULL,
    vehicle_id          UUID        NOT NULL,
    apartment_id        UUID        NOT NULL,
    start_date          DATE        NOT NULL,
    end_date            DATE,                   -- NULL means currently active
    parking_card_number VARCHAR(50),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_parking_assignments PRIMARY KEY (id),
    CONSTRAINT fk_parking_assignments_slot FOREIGN KEY (parking_slot_id) REFERENCES parking_slots (id) ON DELETE RESTRICT,
    CONSTRAINT fk_parking_assignments_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_parking_assignments_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT
);

CREATE INDEX idx_parking_assignments_slot_id ON parking_assignments (parking_slot_id);
CREATE INDEX idx_parking_assignments_vehicle_id ON parking_assignments (vehicle_id);
CREATE INDEX idx_parking_assignments_apartment_id ON parking_assignments (apartment_id);
-- Partial index for active assignments
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

    CONSTRAINT pk_guest_vehicles PRIMARY KEY (id),
    CONSTRAINT fk_guest_vehicles_apartment FOREIGN KEY (host_apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_guest_vehicles_user FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_guest_vehicles_apartment_id ON guest_vehicles (host_apartment_id);
CREATE INDEX idx_guest_vehicles_license_plate ON guest_vehicles (license_plate);
CREATE INDEX idx_guest_vehicles_entry_time ON guest_vehicles (entry_time DESC);

-- ---------------------------------------------------------------------------
-- TABLE: maintenance_categories
-- Types of maintenance work with SLA targets
-- ---------------------------------------------------------------------------
CREATE TABLE maintenance_categories (
    id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    name             VARCHAR(100)    NOT NULL,
    sla_hours        SMALLINT        NOT NULL DEFAULT 24 CHECK (sla_hours > 0),
    priority_default maintenance_priority NOT NULL DEFAULT 'MEDIUM',
    is_active        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_maintenance_categories PRIMARY KEY (id),
    CONSTRAINT uq_maintenance_categories_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- TABLE: maintenance_requests
-- Core maintenance tracking table
-- ---------------------------------------------------------------------------
CREATE TABLE maintenance_requests (
    id                      UUID                    NOT NULL DEFAULT gen_random_uuid(),
    apartment_id            UUID                    NOT NULL,
    submitted_by_user_id    UUID                    NOT NULL,
    category_id             UUID,
    title                   VARCHAR(255)            NOT NULL,
    description             TEXT,
    status                  maintenance_status      NOT NULL DEFAULT 'NEW',
    priority                maintenance_priority    NOT NULL DEFAULT 'MEDIUM',
    assigned_to_user_id     UUID,                               -- internal technician
    assigned_to_contractor_id UUID,                             -- external contractor
    scheduled_date          DATE,
    completed_date          TIMESTAMPTZ,
    rating                  SMALLINT                CHECK (rating BETWEEN 1 AND 5),
    rating_comment          TEXT,
    sla_deadline            TIMESTAMPTZ,
    resolution_notes        TEXT,
    created_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_maintenance_requests PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_requests_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_requests_submitter FOREIGN KEY (submitted_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_requests_category FOREIGN KEY (category_id) REFERENCES maintenance_categories (id) ON DELETE SET NULL,
    CONSTRAINT fk_maintenance_requests_technician FOREIGN KEY (assigned_to_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_maintenance_requests_contractor FOREIGN KEY (assigned_to_contractor_id) REFERENCES contractors (id) ON DELETE SET NULL,
    -- Only one assignee at a time
    CONSTRAINT chk_maintenance_single_assignee CHECK (
        NOT (assigned_to_user_id IS NOT NULL AND assigned_to_contractor_id IS NOT NULL)
    )
);

CREATE INDEX idx_maintenance_requests_apartment_id ON maintenance_requests (apartment_id);
CREATE INDEX idx_maintenance_requests_status ON maintenance_requests (status);
CREATE INDEX idx_maintenance_requests_priority ON maintenance_requests (priority);
CREATE INDEX idx_maintenance_requests_technician ON maintenance_requests (assigned_to_user_id);
CREATE INDEX idx_maintenance_requests_contractor ON maintenance_requests (assigned_to_contractor_id);
CREATE INDEX idx_maintenance_requests_category_id ON maintenance_requests (category_id);
CREATE INDEX idx_maintenance_requests_sla_deadline ON maintenance_requests (sla_deadline) WHERE status NOT IN ('DONE', 'CANCELLED');
CREATE INDEX idx_maintenance_requests_created_at ON maintenance_requests (created_at DESC);

-- ---------------------------------------------------------------------------
-- TABLE: maintenance_photos
-- Photos attached to a maintenance request (before/progress/after)
-- ---------------------------------------------------------------------------
CREATE TABLE maintenance_photos (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    request_id  UUID            NOT NULL,
    file_url    VARCHAR(1000)   NOT NULL,    -- MinIO object key (not full URL; presigned at fetch time)
    file_name   VARCHAR(255),
    mime_type   VARCHAR(100),
    file_size   INTEGER,                    -- bytes
    phase       photo_phase     NOT NULL DEFAULT 'BEFORE',
    uploaded_by UUID,
    uploaded_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_maintenance_photos PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_photos_request FOREIGN KEY (request_id) REFERENCES maintenance_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_photos_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_maintenance_photos_request_id ON maintenance_photos (request_id);
CREATE INDEX idx_maintenance_photos_phase ON maintenance_photos (phase);

-- ---------------------------------------------------------------------------
-- TABLE: maintenance_status_history
-- Immutable log of status transitions for a maintenance request
-- ---------------------------------------------------------------------------
CREATE TABLE maintenance_status_history (
    id                  UUID                NOT NULL DEFAULT gen_random_uuid(),
    request_id          UUID                NOT NULL,
    old_status          maintenance_status,
    new_status          maintenance_status  NOT NULL,
    changed_by_user_id  UUID,
    notes               TEXT,
    changed_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_maintenance_status_history PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_status_history_request FOREIGN KEY (request_id) REFERENCES maintenance_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_maintenance_status_history_request_id ON maintenance_status_history (request_id);
CREATE INDEX idx_maintenance_status_history_changed_at ON maintenance_status_history (changed_at DESC);

-- ---------------------------------------------------------------------------
-- TABLE: amenities
-- Bookable facilities (gym, pool, BBQ, meeting room, etc.)
-- ---------------------------------------------------------------------------
CREATE TABLE amenities (
    id                              UUID            NOT NULL DEFAULT gen_random_uuid(),
    name                            VARCHAR(100)    NOT NULL,
    description                     TEXT,
    location                        VARCHAR(255),
    capacity                        SMALLINT        CHECK (capacity > 0),
    opening_time                    TIME            NOT NULL DEFAULT '06:00',
    closing_time                    TIME            NOT NULL DEFAULT '22:00',
    max_daily_bookings_per_resident SMALLINT        NOT NULL DEFAULT 1 CHECK (max_daily_bookings_per_resident > 0),
    requires_approval               BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active                       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_amenities PRIMARY KEY (id),
    CONSTRAINT uq_amenities_name UNIQUE (name),
    CONSTRAINT chk_amenities_hours CHECK (opening_time < closing_time)
);

-- ---------------------------------------------------------------------------
-- TABLE: amenity_bookings
-- Resident reservations for building amenities
-- ---------------------------------------------------------------------------
CREATE TABLE amenity_bookings (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    amenity_id          UUID            NOT NULL,
    resident_id         UUID            NOT NULL,   -- references residents.id (not users.id)
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

    CONSTRAINT pk_amenity_bookings PRIMARY KEY (id),
    CONSTRAINT fk_amenity_bookings_amenity FOREIGN KEY (amenity_id) REFERENCES amenities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_amenity_bookings_resident FOREIGN KEY (resident_id) REFERENCES residents (id) ON DELETE RESTRICT,
    CONSTRAINT fk_amenity_bookings_apartment FOREIGN KEY (apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_amenity_bookings_approver FOREIGN KEY (approved_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_amenity_bookings_times CHECK (start_time < end_time)
);

CREATE INDEX idx_amenity_bookings_amenity_id ON amenity_bookings (amenity_id);
CREATE INDEX idx_amenity_bookings_resident_id ON amenity_bookings (resident_id);
CREATE INDEX idx_amenity_bookings_apartment_id ON amenity_bookings (apartment_id);
CREATE INDEX idx_amenity_bookings_status ON amenity_bookings (status);
-- Composite index for conflict/availability checks
CREATE INDEX idx_amenity_bookings_date_amenity ON amenity_bookings (amenity_id, booking_date, status);

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
CREATE INDEX idx_contractors_rating ON contractors (rating DESC NULLS LAST);

-- ---------------------------------------------------------------------------
-- TABLE: contracts
-- Service contracts with vendors
-- ---------------------------------------------------------------------------
CREATE TABLE contracts (
    id              UUID                NOT NULL DEFAULT gen_random_uuid(),
    contractor_id   UUID                NOT NULL,
    title           VARCHAR(255)        NOT NULL,
    scope           TEXT,
    contract_value  NUMERIC(18, 2),
    currency        VARCHAR(3)          NOT NULL DEFAULT 'VND',
    start_date      DATE                NOT NULL,
    end_date        DATE,
    status          contract_status     NOT NULL DEFAULT 'PENDING',
    attachment_url  VARCHAR(1000),      -- MinIO object key for contract PDF
    notes           TEXT,
    created_by_user_id UUID,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_contracts PRIMARY KEY (id),
    CONSTRAINT fk_contracts_contractor FOREIGN KEY (contractor_id) REFERENCES contractors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_contracts_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_contracts_dates CHECK (end_date IS NULL OR end_date > start_date)
);

CREATE INDEX idx_contracts_contractor_id ON contracts (contractor_id);
CREATE INDEX idx_contracts_status ON contracts (status);
CREATE INDEX idx_contracts_end_date ON contracts (end_date) WHERE status = 'ACTIVE';   -- For expiry alerts

-- ---------------------------------------------------------------------------
-- TABLE: contract_payments
-- Payment records against a contract (record-only, no disbursement workflow)
-- ---------------------------------------------------------------------------
CREATE TABLE contract_payments (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    contract_id         UUID            NOT NULL,
    amount              NUMERIC(18, 2)  NOT NULL CHECK (amount > 0),
    payment_date        DATE            NOT NULL,
    description         TEXT,
    reference_number    VARCHAR(100),   -- bank transfer reference or invoice number
    recorded_by_user_id UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_contract_payments PRIMARY KEY (id),
    CONSTRAINT fk_contract_payments_contract FOREIGN KEY (contract_id) REFERENCES contracts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_payments_user FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_contract_payments_contract_id ON contract_payments (contract_id);
CREATE INDEX idx_contract_payments_payment_date ON contract_payments (payment_date DESC);

-- ---------------------------------------------------------------------------
-- TABLE: maintenance_schedules
-- Recurring maintenance tasks tied to a contract
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

    CONSTRAINT pk_maintenance_schedules PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_schedules_contract FOREIGN KEY (contract_id) REFERENCES contracts (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_schedules_contract_id ON maintenance_schedules (contract_id);
CREATE INDEX idx_maintenance_schedules_next_due_date ON maintenance_schedules (next_due_date) WHERE is_active = TRUE;

-- ---------------------------------------------------------------------------
-- TABLE: announcements
-- Building-wide or targeted notices
-- ---------------------------------------------------------------------------
CREATE TABLE announcements (
    id                  UUID                    NOT NULL DEFAULT gen_random_uuid(),
    title               VARCHAR(255)            NOT NULL,
    content             TEXT                    NOT NULL,
    type                announcement_type       NOT NULL DEFAULT 'GENERAL',
    target_scope        announcement_scope      NOT NULL DEFAULT 'ALL',
    target_block_id     UUID,                               -- set when scope = BLOCK or FLOOR
    target_floor        SMALLINT,                           -- set when scope = FLOOR
    send_push           BOOLEAN                 NOT NULL DEFAULT TRUE,
    send_email          BOOLEAN                 NOT NULL DEFAULT FALSE,
    send_sms            BOOLEAN                 NOT NULL DEFAULT FALSE,
    attachment_url      VARCHAR(1000),
    created_by_user_id  UUID                    NOT NULL,
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,                        -- NULL = draft; set when published
    updated_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_announcements PRIMARY KEY (id),
    CONSTRAINT fk_announcements_block FOREIGN KEY (target_block_id) REFERENCES blocks (id) ON DELETE SET NULL,
    CONSTRAINT fk_announcements_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_announcements_scope CHECK (
        (target_scope = 'ALL') OR
        (target_scope = 'BLOCK' AND target_block_id IS NOT NULL) OR
        (target_scope = 'FLOOR' AND target_block_id IS NOT NULL AND target_floor IS NOT NULL)
    )
);

CREATE INDEX idx_announcements_type ON announcements (type);
CREATE INDEX idx_announcements_scope ON announcements (target_scope);
CREATE INDEX idx_announcements_block_id ON announcements (target_block_id);
CREATE INDEX idx_announcements_published_at ON announcements (published_at DESC) WHERE published_at IS NOT NULL;
CREATE INDEX idx_announcements_created_by ON announcements (created_by_user_id);

-- ---------------------------------------------------------------------------
-- TABLE: announcement_reads
-- Read receipts per user per announcement
-- ---------------------------------------------------------------------------
CREATE TABLE announcement_reads (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    announcement_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    read_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_announcement_reads PRIMARY KEY (id),
    CONSTRAINT fk_announcement_reads_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_reads_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_announcement_reads UNIQUE (announcement_id, user_id)
);

CREATE INDEX idx_announcement_reads_user_id ON announcement_reads (user_id);
CREATE INDEX idx_announcement_reads_announcement_id ON announcement_reads (announcement_id);

-- ---------------------------------------------------------------------------
-- TABLE: audit_logs
-- Immutable audit trail for all significant system actions
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID,                               -- NULL for system-triggered actions
    action          VARCHAR(100) NOT NULL,              -- e.g., 'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'STATUS_CHANGE'
    entity_type     VARCHAR(100) NOT NULL,              -- e.g., 'MaintenanceRequest', 'Resident'
    entity_id       UUID,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
    -- No FK on user_id intentionally — audit log must survive user deletion
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
-- GIN index for JSONB search (useful for searching changed values)
CREATE INDEX idx_audit_logs_new_value_gin ON audit_logs USING GIN (new_value);

-- ---------------------------------------------------------------------------
-- TABLE: notifications
-- In-app notifications for users (also used to track FCM/email delivery)
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id              UUID                NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID                NOT NULL,
    title           VARCHAR(255)        NOT NULL,
    body            TEXT,
    type            notification_type   NOT NULL DEFAULT 'GENERAL',
    reference_id    UUID,               -- ID of the referenced entity (request, booking, etc.)
    reference_type  VARCHAR(100),       -- entity type name
    is_read         BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_is_read ON notifications (user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
CREATE INDEX idx_notifications_type ON notifications (type);

-- =============================================================================
-- SEED DATA — Reference / lookup data required for system operation
-- =============================================================================

-- Default maintenance categories with SLA targets
INSERT INTO maintenance_categories (name, sla_hours, priority_default) VALUES
    ('Electrical',          8,  'HIGH'),
    ('Plumbing',            8,  'HIGH'),
    ('Air Conditioning',    24, 'MEDIUM'),
    ('Elevator',            4,  'URGENT'),
    ('Common Area',         48, 'LOW'),
    ('Pest Control',        24, 'MEDIUM'),
    ('Structural',          24, 'HIGH'),
    ('Internet / Cable TV', 24, 'MEDIUM'),
    ('Other',               48, 'LOW');

-- Default admin user (password: Admin@123456 — MUST be changed immediately)
-- BCrypt hash of 'Admin@123456' with strength 12
INSERT INTO users (email, phone, full_name, password_hash, role, is_active) VALUES
    ('admin@gemek.vn', '0900000000', 'System Administrator',
     '$2a$12$placeholder_bcrypt_hash_change_on_first_login',
     'ADMIN', TRUE);

-- =============================================================================
-- FOREIGN KEY: maintenance_requests references contractors
-- Declared here because contractors table is defined after maintenance_requests above
-- (Alternatively reorder tables — kept separate for readability)
-- =============================================================================
-- Note: The FK fk_maintenance_requests_contractor is declared inline above.
-- PostgreSQL resolves this if contractors table exists at DDL time.
-- In Flyway migrations, split into V1 (tables without circular deps) and V2 (add remaining FKs).
