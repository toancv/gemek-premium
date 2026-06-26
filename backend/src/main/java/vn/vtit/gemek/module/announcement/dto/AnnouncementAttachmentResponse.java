/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO describing one announcement attachment row (authoring view: upload/list).
 *
 * <p>Deliberately carries NO download URL: presigning happens on the detail read
 * ({@code GET /api/announcements/{id}} → {@code attachments[].downloadUrl}) through the C2.1 scope
 * gate, as a short-lived FORCED-DOWNLOAD URL — not eagerly here.
 */
@Getter
@Builder
public class AnnouncementAttachmentResponse {

    /** Attachment row id. */
    private final UUID id;

    /** Original client filename, shown to the user. */
    private final String displayFilename;

    /** Detected (Tika) content type stored at upload. */
    private final String contentType;

    /** Object size in bytes. */
    private final Long sizeBytes;

    /** Row creation timestamp. */
    private final OffsetDateTime createdAt;
}
