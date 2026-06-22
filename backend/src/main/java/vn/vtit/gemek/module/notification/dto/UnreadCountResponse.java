/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response DTO carrying the count of unread notifications for the requesting user.
 *
 * <p>Returned from {@code GET /api/notifications/unread-count}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountResponse {

    /** The number of unread notifications for the requesting user. */
    private long unreadCount;
}
