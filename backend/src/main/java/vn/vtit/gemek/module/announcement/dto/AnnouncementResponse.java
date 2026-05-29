/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.dto;

import lombok.Builder;
import lombok.Getter;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;

import java.time.OffsetDateTime;
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
    private final AnnouncementScope scope;

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

    // =========================================================================
    // Nested reference types
    // =========================================================================

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
