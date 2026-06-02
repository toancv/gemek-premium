-- =============================================================================
-- V2: Seed default admin user
-- Version: 1.0
-- Date: 2026-05-29
-- =============================================================================
-- Default admin credentials:
--   Email:    admin@gemek.vn
--   Password hash: supplied at deploy time via ADMIN_PASSWORD_HASH environment variable.
-- IMPORTANT: Set ADMIN_PASSWORD_HASH to a BCrypt-12 hash of a strong, unique password.
-- =============================================================================

INSERT INTO users (email, phone, full_name, password_hash, role, is_active)
VALUES (
    'admin@gemek.vn',
    '0900000000',
    'Quan tri vien',
    '${ADMIN_PASSWORD_HASH}',
    'ADMIN',
    TRUE
);
