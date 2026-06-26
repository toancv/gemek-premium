-- =============================================================================
-- V22: Create announcement_attachment table (C3 — downloadable document attachments)
-- Version: 1.0
-- Date: 2026-06-26
-- =============================================================================
--
-- Stores DOWNLOADABLE document attachments (pdf|docx|xlsx|pptx|txt) attached to an
-- announcement — DISTINCT from announcement_media (cover/inline IMAGES). Rows are created
-- ONLY for draft announcements by ADMIN (published = immutable). Object bytes live in MinIO
-- under the SAME C2.1 key convention announcements/{announcement_id}/{uuid} so the EXISTING
-- presign access gate (AnnouncementService.assertMediaPresignAccess, which parses the
-- announcement id from the key) applies unchanged.
--
-- Attachments are served strictly as FORCED DOWNLOAD (Content-Disposition: attachment +
-- response-content-type=application/octet-stream, signed into the presigned URL) — never
-- inline-rendered. There is NO kind discriminator (attachments are a flat list, no
-- cover/inline semantics).
--
-- FK ON DELETE CASCADE mirrors announcement_media -> announcements (V21): deleting a draft
-- announcement removes its attachment rows at the DB layer. The service ALSO collects the
-- object keys before the delete and schedules an AFTER-COMMIT MinIO cleanup, so DB rows and
-- objects stay consistent.
-- ---------------------------------------------------------------------------

CREATE TABLE announcement_attachment (
    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),
    announcement_id    UUID            NOT NULL,
    object_key         TEXT            NOT NULL,
    content_type       VARCHAR(100),
    size_bytes         BIGINT,
    display_filename   VARCHAR(255)    NOT NULL,
    created_by         UUID,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_announcement_attachment PRIMARY KEY (id),
    CONSTRAINT fk_announcement_attachment_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_attachment_creator
        FOREIGN KEY (created_by)      REFERENCES users(id)         ON DELETE SET NULL
);

CREATE INDEX idx_announcement_attachment_announcement_id ON announcement_attachment (announcement_id);
