/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.dto;

import lombok.Builder;
import lombok.Getter;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMediaKind;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO describing one announcement media row.
 *
 * <p>Deliberately carries NO presigned URL: presigning happens on read via
 * {@code GET /api/files/presign} (C2.1 scope gate, 10-minute expiry), keyed by the
 * stored object key — not eagerly here.
 */
@Getter
@Builder
public class AnnouncementMediaResponse {

    /** Media row id. */
    private final UUID id;

    /** Media kind (COVER|INLINE). */
    private final AnnouncementMediaKind kind;

    /** Detected (Tika) content type stored at upload. */
    private final String contentType;

    /** Object size in bytes. */
    private final Long sizeBytes;

    /** Original client filename (display only). */
    private final String originalFilename;

    /** Stored MinIO object key (C2.1 convention) — the value clients pass to the presign endpoint. */
    private final String objectKey;

    /** Row creation timestamp. */
    private final OffsetDateTime createdAt;
}
