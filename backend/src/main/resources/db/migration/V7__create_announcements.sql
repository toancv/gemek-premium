-- =============================================================================
-- V7: Create announcements and announcement_reads tables
-- Version: 1.0
-- Date: 2026-05-29
-- =============================================================================

-- ---------------------------------------------------------------------------
-- TABLE: announcements
-- Stores building-wide or targeted announcements created by admins.
-- ---------------------------------------------------------------------------

CREATE TABLE announcements (
    id                  UUID                    NOT NULL DEFAULT gen_random_uuid(),
    title               VARCHAR(255)            NOT NULL,
    content             TEXT                    NOT NULL,
    type                announcement_type       NOT NULL DEFAULT 'GENERAL',
    scope               announcement_scope      NOT NULL DEFAULT 'ALL',
    target_block_id     UUID,
    target_floor        SMALLINT,
    send_push           BOOLEAN                 NOT NULL DEFAULT TRUE,
    send_email          BOOLEAN                 NOT NULL DEFAULT FALSE,
    send_sms            BOOLEAN                 NOT NULL DEFAULT FALSE,
    created_by_user_id  UUID,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_announcements             PRIMARY KEY (id),
    CONSTRAINT fk_announcements_block       FOREIGN KEY (target_block_id)     REFERENCES blocks(id)  ON DELETE SET NULL,
    CONSTRAINT fk_announcements_creator     FOREIGN KEY (created_by_user_id)  REFERENCES users(id)   ON DELETE SET NULL,
    -- BLOCK scope requires target_block_id; FLOOR scope requires both
    CONSTRAINT chk_announcement_scope CHECK (
        (scope = 'ALL') OR
        (scope = 'BLOCK' AND target_block_id IS NOT NULL) OR
        (scope = 'FLOOR' AND target_block_id IS NOT NULL AND target_floor IS NOT NULL)
    )
);

CREATE INDEX idx_announcements_scope        ON announcements (scope);
CREATE INDEX idx_announcements_type         ON announcements (type);
CREATE INDEX idx_announcements_published_at ON announcements (published_at DESC NULLS LAST);
CREATE INDEX idx_announcements_block        ON announcements (target_block_id) WHERE target_block_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- TABLE: announcement_reads
-- Tracks which users have read which announcements (idempotent mark-read).
-- ---------------------------------------------------------------------------

CREATE TABLE announcement_reads (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    announcement_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    read_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_announcement_reads              PRIMARY KEY (id),
    CONSTRAINT fk_ar_announcement FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_user         FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT uq_ar_unique       UNIQUE (announcement_id, user_id)
);

CREATE INDEX idx_ar_announcement_id ON announcement_reads (announcement_id);
CREATE INDEX idx_ar_user_id         ON announcement_reads (user_id);
