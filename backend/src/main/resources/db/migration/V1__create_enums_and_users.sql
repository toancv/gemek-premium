-- =============================================================================
-- V1: Create pgcrypto extension, ENUM types, and users table
-- Version: 1.0
-- Date: 2026-05-29
-- =============================================================================

-- Enable pgcrypto for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- ENUM types required by the users table (scope for this migration)
-- ---------------------------------------------------------------------------

CREATE TYPE user_role AS ENUM (
    'ADMIN',
    'TECHNICIAN',
    'RESIDENT',
    'BOARD_MEMBER'
);

-- ---------------------------------------------------------------------------
-- Additional ENUM types needed by future modules — created here so Flyway
-- does not fail if later migration scripts reference them.
-- ---------------------------------------------------------------------------

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
-- Central identity table. All roles share this table.
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    email           VARCHAR(255)    NOT NULL,
    phone           VARCHAR(20),
    full_name       VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            user_role       NOT NULL DEFAULT 'RESIDENT',
    fcm_token       VARCHAR(500),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    avatar_url      VARCHAR(1000),
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users         PRIMARY KEY (id),
    CONSTRAINT uq_users_email   UNIQUE (email)
);

CREATE INDEX idx_users_role      ON users (role);
CREATE INDEX idx_users_is_active ON users (is_active);
CREATE INDEX idx_users_phone     ON users (phone);
