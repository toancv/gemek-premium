-- =============================================================================
-- V2: Seed default admin user
-- Version: 1.0
-- Date: 2026-05-29
-- =============================================================================
-- Default admin credentials:
--   Email:    admin@gemek.vn
--   Password: Admin@123456
--   BCrypt hash generated with strength 12.
-- IMPORTANT: Change this password immediately after first login.
-- =============================================================================

INSERT INTO users (email, phone, full_name, password_hash, role, is_active)
VALUES (
    'admin@gemek.vn',
    '0900000000',
    'Quan tri vien',
    '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'ADMIN',
    TRUE
);
