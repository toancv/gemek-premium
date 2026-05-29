-- =============================================================================
-- V10: Create notifications and audit_logs tables
-- Version: 1.0
-- Date: 2026-05-29
-- =============================================================================

-- ---------------------------------------------------------------------------
-- TABLE: notifications
-- In-app notification records per user.
-- ENUM notification_type is defined in V1.
-- ---------------------------------------------------------------------------

CREATE TABLE notifications (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    title           VARCHAR(255)    NOT NULL,
    body            TEXT,
    type            notification_type NOT NULL,
    reference_id    UUID,
    reference_type  VARCHAR(100),
    is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user_id    ON notifications (user_id);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE is_read = FALSE;

-- ---------------------------------------------------------------------------
-- TABLE: audit_logs
-- Immutable record of state-changing operations.
-- user_id is nullable to support system-initiated actions.
-- ---------------------------------------------------------------------------

CREATE TABLE audit_logs (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID,
    action          VARCHAR(100)    NOT NULL,
    entity_type     VARCHAR(100)    NOT NULL,
    entity_id       UUID,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_logs_user_id    ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_entity     ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
