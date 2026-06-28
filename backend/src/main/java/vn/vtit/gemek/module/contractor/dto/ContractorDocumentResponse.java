/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO describing one contractor document row.
 *
 * <p>{@link #downloadUrl} is a short-lived FORCED-DOWNLOAD presigned URL minted ONLY by the list
 * endpoint ({@code GET /api/contractors/{id}/documents}) after the staff-only presign gate passes;
 * it is {@code null} on the upload response (no URL is eagerly minted at upload time).
 */
@Getter
@Builder
public class ContractorDocumentResponse {

    /** Document row id. */
    private final UUID id;

    /** Original client filename, shown to the user. */
    private final String displayFilename;

    /** Detected (Tika) content type stored at upload. */
    private final String contentType;

    /** Object size in bytes. */
    private final Long sizeBytes;

    /** Row creation timestamp. */
    private final OffsetDateTime createdAt;

    /** Fresh presigned FORCED-DOWNLOAD URL; {@code null} on the upload response. */
    private final String downloadUrl;
}
