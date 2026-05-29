/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;

import java.util.UUID;

/**
 * Request DTO for updating a draft announcement.
 *
 * <p>All fields are optional — only non-null values are applied.
 * Updates are rejected with a {@code 409 CONFLICT} if the announcement has already been published.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateAnnouncementRequest {

    /** New headline text. {@code null} means no change. */
    private String title;

    /** New body text. {@code null} means no change. */
    private String content;

    /** New content category. {@code null} means no change. */
    private AnnouncementType type;

    /** New delivery scope. {@code null} means no change. */
    private AnnouncementScope scope;

    /**
     * New target block UUID.
     * {@code null} means no change (use a scope of {@link AnnouncementScope#ALL} to clear targeting).
     */
    private UUID targetBlockId;

    /** New target floor number. {@code null} means no change. */
    private Short targetFloor;

    /** New push delivery flag. {@code null} means no change. */
    private Boolean sendPush;

    /** New email delivery flag. {@code null} means no change. */
    private Boolean sendEmail;

    /** New SMS delivery flag. {@code null} means no change. */
    private Boolean sendSms;
}
