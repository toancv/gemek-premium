-- =============================================================================
-- V21: Create announcement_media table (C2.2 — announcement rich content images)
-- Version: 1.0
-- Date: 2026-06-23
-- =============================================================================
--
-- Stores image media (cover|inline) attached to an announcement. Rows are created
-- ONLY for draft announcements by ADMIN (published = immutable). Object bytes live in
-- MinIO under the C2.1 key convention announcements/{announcement_id}/{uuid}; this table
-- holds the metadata + object key the presign gate parses (AnnouncementService.MEDIA_KEY_PREFIX).
--
-- FK ON DELETE CASCADE mirrors ticket_photos -> tickets (V5): deleting a draft announcement
-- removes its media rows at the DB layer. The service ALSO collects the object keys before the
-- delete and schedules an AFTER-COMMIT MinIO cleanup, so DB rows and objects stay consistent.
-- ---------------------------------------------------------------------------

CREATE TABLE announcement_media (
    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),
    announcement_id    UUID            NOT NULL,
    object_key         TEXT            NOT NULL,
    content_type       VARCHAR(100),
    size_bytes         BIGINT,
    kind               VARCHAR(20)     NOT NULL DEFAULT 'INLINE',
    original_filename  VARCHAR(255),
    created_by         UUID,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_announcement_media PRIMARY KEY (id),
    CONSTRAINT fk_announcement_media_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_media_creator
        FOREIGN KEY (created_by)      REFERENCES users(id)         ON DELETE SET NULL,
    -- kind is a closed set (cover|inline); stored uppercase to match the JPA enum name.
    CONSTRAINT chk_announcement_media_kind CHECK (kind IN ('COVER', 'INLINE'))
);

CREATE INDEX idx_announcement_media_announcement_id ON announcement_media (announcement_id);
