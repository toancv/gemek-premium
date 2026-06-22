-- =============================================================================
-- V12: Phone-as-login schema change
-- Login identifier switches from email to phone number.
-- Canonical form (0xxxxxxxxx, 10 digits) is enforced by PhoneUtils on the
-- backend; no DB CHECK constraint is required here.
-- DB is reset before this migration runs, so no data backfill is needed.
-- =============================================================================

-- phone: promote to NOT NULL + UNIQUE login key
ALTER TABLE users
    ALTER COLUMN phone SET NOT NULL;

-- Note: idx_users_phone (plain index) already exists from V1.
-- The UNIQUE constraint below creates its own implicit index (uq_users_phone).
-- The original idx_users_phone becomes redundant but is left in place
-- to avoid a DROP that would require coordinating with V1's checksum.
ALTER TABLE users
    ADD CONSTRAINT uq_users_phone UNIQUE (phone);

-- email: informational only — drop NOT NULL, keep existing UNIQUE constraint.
-- Postgres allows multiple NULLs under a UNIQUE constraint (intended behaviour).
ALTER TABLE users
    ALTER COLUMN email DROP NOT NULL;
