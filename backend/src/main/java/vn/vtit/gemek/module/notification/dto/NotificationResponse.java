/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a single notification record.
 *
 * <p>Returned from {@code GET /api/notifications} as items in the paginated list.
 * The {@code type} field is the string name of the
 * {@link vn.vtit.gemek.module.notification.entity.NotificationType} enum constant.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    /** Unique identifier of the notification. */
    private UUID id;

    /** Short subject line of the notification. */
    private String title;

    /** Optional full body text of the notification. */
    private String body;

    /** String name of the {@link vn.vtit.gemek.module.notification.entity.NotificationType} constant. */
    private String type;

    /** Optional UUID of the related entity. */
    private UUID referenceId;

    /** Optional entity-type label paired with {@code referenceId}. */
    private String referenceType;

    /** Whether the user has marked this notification as read. */
    @JsonProperty("isRead")
    private boolean isRead;

    /** Timestamp when the notification was created. */
    private OffsetDateTime createdAt;
}
