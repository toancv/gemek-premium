/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;

import java.util.UUID;

/**
 * Request DTO for creating a new announcement draft.
 *
 * <p>Scope-specific validation rules are enforced in the service layer:
 * <ul>
 *   <li>{@link AnnouncementScope#BLOCK} requires {@code targetBlockId}.</li>
 *   <li>{@link AnnouncementScope#FLOOR} requires both {@code targetBlockId} and {@code targetFloor}.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateAnnouncementRequest {

    /** Announcement headline. Must not be blank. */
    @NotBlank
    private String title;

    /** Full body text. Must not be blank. */
    @NotBlank
    private String content;

    /** Content category. Required. */
    @NotNull
    private AnnouncementType type;

    /** Delivery scope. Required. */
    @NotNull
    private AnnouncementScope scope;

    /**
     * Target block UUID for BLOCK or FLOOR scoped announcements.
     * Must be provided when {@code scope} is not {@link AnnouncementScope#ALL}.
     */
    private UUID targetBlockId;

    /**
     * Target floor number for FLOOR scoped announcements.
     * Must be provided when {@code scope} is {@link AnnouncementScope#FLOOR}.
     */
    private Short targetFloor;

    /** Whether to deliver via push notification. Defaults to {@code true} in service if null. */
    private Boolean sendPush;

    /** Whether to deliver via email. Defaults to {@code false} in service if null. */
    private Boolean sendEmail;

    /** Whether to deliver via SMS. Defaults to {@code false} in service if null. */
    private Boolean sendSms;
}
