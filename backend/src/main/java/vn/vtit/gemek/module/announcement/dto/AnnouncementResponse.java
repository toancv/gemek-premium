/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMediaKind;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO representing a single announcement.
 *
 * <p>Returned by all read endpoints. Includes a {@code readByCount} aggregate
 * showing how many users have marked the announcement as read.
 */
@Getter
@Builder
public class AnnouncementResponse {

    /** Announcement UUID. */
    private final UUID id;

    /** Short headline text. */
    private final String title;

    /** Full body text. */
    private final String content;

    /** Content category. */
    private final AnnouncementType type;

    /** Delivery scope. */
    private final AnnouncementScope targetScope;

    /**
     * Target block reference.
     * {@code null} when {@code scope} is {@link AnnouncementScope#ALL}.
     */
    private final BlockRef targetBlock;

    /**
     * Target floor number.
     * {@code null} unless {@code scope} is {@link AnnouncementScope#FLOOR}.
     */
    private final Short targetFloor;

    /** Whether push notification delivery is requested. */
    private final boolean sendPush;

    /** Whether email delivery is requested. */
    private final boolean sendEmail;

    /** Whether SMS delivery is requested. */
    private final boolean sendSms;

    /**
     * Creator reference.
     * {@code null} if the creating admin account was subsequently deleted.
     */
    private final UserRef createdBy;

    /**
     * Timestamp at which the announcement was published.
     * {@code null} means the announcement is still a draft.
     */
    private final OffsetDateTime publishedAt;

    /** Record creation timestamp. */
    private final OffsetDateTime createdAt;

    /** Total number of users who have marked this announcement as read. */
    private final long readByCount;

    /**
     * Whether the requesting user has marked this announcement as read.
     * Always {@code false} on mutation responses (create/update/publish) — a draft or
     * just-published announcement cannot yet have a read record for the caller.
     */
    @JsonProperty("isRead")
    private final boolean isRead;

    /**
     * Media manifest for rendering: each entry maps a media row id to a FRESH presigned GET URL
     * (minted per request through the C2.1 scope gate). Populated ONLY on the detail response and
     * ONLY for media the caller may access; {@code null}/empty elsewhere (list/create/update/publish).
     * The resident renderer resolves {@code announcement-media:{id}} placeholders against these
     * entries and renders the COVER entry as a banner.
     */
    private final List<MediaRef> media;

    /**
     * Attachment manifest for download: each entry maps an attachment row id to a FRESH short-lived
     * FORCED-DOWNLOAD presigned URL (minted per request through the C2.1 scope gate). Populated ONLY on
     * the detail response and ONLY for attachments the caller may access; {@code null}/empty elsewhere
     * (list/create/update/publish). Distinct from {@link #media} (inline/cover images): attachments are
     * a flat downloadable list, never rendered inline.
     */
    private final List<AttachmentRef> attachments;

    // =========================================================================
    // Nested reference types
    // =========================================================================

    /**
     * One attachment manifest entry — an attachment row id paired with a short-lived forced-download URL.
     */
    @Getter
    @Builder
    public static class AttachmentRef {

        /** Attachment row id. */
        private final UUID id;

        /** Original filename, shown to the user. */
        private final String displayFilename;

        /** Object size in bytes. */
        private final Long sizeBytes;

        /** Fresh presigned FORCED-DOWNLOAD URL (short-lived); never a raw object key or long-lived URL. */
        private final String downloadUrl;
    }

    /**
     * One media manifest entry — a media row id paired with a short-lived presigned URL.
     */
    @Getter
    @Builder
    public static class MediaRef {

        /** Media row id — the {@code {id}} in an {@code announcement-media:{id}} placeholder. */
        private final UUID id;

        /** COVER (rendered as a banner) or INLINE (resolved inside the Markdown body). */
        private final AnnouncementMediaKind kind;

        /** Fresh presigned GET URL (short-lived); never a raw object key or long-lived URL. */
        private final String url;
    }

    /**
     * Minimal block reference embedded in {@link AnnouncementResponse}.
     */
    @Getter
    @Builder
    public static class BlockRef {

        /** Block UUID. */
        private final UUID id;

        /** Block display name. */
        private final String name;
    }

    /**
     * Minimal user reference embedded in {@link AnnouncementResponse}.
     */
    @Getter
    @Builder
    public static class UserRef {

        /** User UUID. */
        private final UUID id;

        /** User display name. */
        private final String fullName;
    }
}
