-- =============================================================================
-- V23: Create contractor_document table (contractor documents — staff-only forced-download)
-- Version: 1.0
-- Date: 2026-06-28
-- =============================================================================
--
-- Stores DOWNLOADABLE document files (pdf|docx|xlsx|pptx|txt) attached to a CONTRACTOR as a
-- row-per-file list (CTO ruling DECISIONS 2026-06-28: contract documents attach to the CONTRACTOR
-- entity, reusing the C3 forced-download attachment stack — NOT to the existing Contract entity).
--
-- This SUPERSEDES the spec'd-but-unbuilt POST/GET /api/contracts/{id}/attachment endpoints and the
-- dormant contracts.attachment_url column, which are kept write-idle and NOT dropped.
--
-- Object bytes live in MinIO under the key convention contractors/{contractor_id}/documents/{uuid}
-- so the staff-only presign access gate (ContractorService.assertContractorDocumentPresignAccess,
-- which parses the contractor id from the key) applies. Documents are served strictly as FORCED
-- DOWNLOAD (Content-Disposition: attachment + response-content-type=application/octet-stream, signed
-- into the presigned URL) — never inline-rendered.
--
-- FK ON DELETE CASCADE mirrors announcement_attachment -> announcements (V22): deleting a contractor
-- removes its document rows at the DB layer. The service ALSO collects object keys before a row delete
-- and schedules an AFTER-COMMIT MinIO cleanup, so DB rows and objects stay consistent.
-- ---------------------------------------------------------------------------

CREATE TABLE contractor_document (
    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),
    contractor_id      UUID            NOT NULL,
    object_key         TEXT            NOT NULL,
    content_type       VARCHAR(100),
    size_bytes         BIGINT,
    display_filename   VARCHAR(255)    NOT NULL,
    created_by         UUID,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_contractor_document PRIMARY KEY (id),
    CONSTRAINT fk_contractor_document_contractor
        FOREIGN KEY (contractor_id) REFERENCES contractors(id) ON DELETE CASCADE,
    CONSTRAINT fk_contractor_document_creator
        FOREIGN KEY (created_by)    REFERENCES users(id)       ON DELETE SET NULL
);

CREATE INDEX idx_contractor_document_contractor_id ON contractor_document (contractor_id);
