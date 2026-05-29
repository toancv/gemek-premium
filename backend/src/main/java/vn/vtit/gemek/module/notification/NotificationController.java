/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.notification.dto.NotificationResponse;
import vn.vtit.gemek.module.notification.dto.UnreadCountResponse;
import vn.vtit.gemek.module.notification.entity.Notification;

import java.util.UUID;

/**
 * REST controller for in-app notification endpoints.
 *
 * <p>All endpoints require an authenticated session. Notifications are always
 * scoped to the requesting user — no user can read or modify another user's notifications.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/notifications              — list the caller's notifications (paginated)</li>
 *   <li>POST /api/notifications/{id}/read    — mark a single notification as read</li>
 *   <li>POST /api/notifications/read-all     — mark all notifications as read</li>
 *   <li>GET  /api/notifications/unread-count — return the unread count</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications", description = "In-app notification management for the authenticated user")
public class NotificationController {

    /** Service handling notification business logic. */
    private final NotificationService notificationService;

    /**
     * Constructs the controller with the notification service dependency.
     *
     * @param notificationService the notification service.
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // =========================================================================
    // List
    // =========================================================================

    /**
     * Returns a paginated list of the caller's notifications, newest first.
     *
     * @param page      0-based page index (default 0).
     * @param size      page size (default 20, max 100).
     * @param principal the authenticated caller.
     * @return 200 OK with a page of notification response DTOs.
     */
    @GetMapping
    @Operation(summary = "List my notifications")
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize);
        Page<Notification> notificationPage =
                notificationService.getMyNotifications(principal.getId(), pageable);

        Page<NotificationResponse> responsePage = notificationPage.map(this::toResponse);
        return ResponseEntity.ok(PageResponse.of(responsePage));
    }

    // =========================================================================
    // Mark single as read
    // =========================================================================

    /**
     * Marks a single notification as read.
     *
     * <p>Returns 404 if the notification does not exist or belongs to a different user.
     *
     * @param id        the UUID of the notification to mark as read.
     * @param principal the authenticated caller.
     * @return 200 OK with no body on success.
     */
    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        notificationService.markAsRead(id, principal.getId());
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // Mark all as read
    // =========================================================================

    /**
     * Marks all unread notifications for the caller as read.
     *
     * @param principal the authenticated caller.
     * @return 200 OK with no body.
     */
    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {

        notificationService.markAllAsRead(principal.getId());
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // Unread count
    // =========================================================================

    /**
     * Returns the count of unread notifications for the authenticated caller.
     *
     * @param principal the authenticated caller.
     * @return 200 OK with the unread count.
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {

        long count = notificationService.countUnread(principal.getId());
        return ResponseEntity.ok(UnreadCountResponse.builder().unreadCount(count).build());
    }

    // =========================================================================
    // Mapping helper
    // =========================================================================

    /**
     * Maps a {@link Notification} entity to a {@link NotificationResponse} DTO.
     *
     * @param notification the entity to map.
     * @return the response DTO.
     */
    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .type(notification.getType().name())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
