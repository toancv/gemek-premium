/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;

import java.util.UUID;

/**
 * Service contract for in-app notification management.
 *
 * <p>Provides creation, retrieval, and read-state management for
 * {@link Notification} records. All operations are scoped to the
 * owning user to prevent cross-user data access.
 */
public interface NotificationService {

    /**
     * Creates and persists a new notification addressed to the given user.
     *
     * @param userId        the UUID of the recipient user.
     * @param title         short subject line for the notification.
     * @param body          optional full body text; may be {@code null}.
     * @param type          the event category that triggered this notification.
     * @param referenceId   optional UUID of the related entity; may be {@code null}.
     * @param referenceType optional entity-type label paired with {@code referenceId}; may be {@code null}.
     */
    void createNotification(UUID userId, String title, String body,
                            NotificationType type, UUID referenceId, String referenceType);

    /**
     * Returns a paginated list of notifications for the given user, newest first.
     *
     * @param userId   the UUID of the requesting user.
     * @param pageable pagination parameters.
     * @return a page of {@link Notification} entities.
     */
    Page<Notification> getMyNotifications(UUID userId, Pageable pageable);

    /**
     * Marks a single notification as read.
     *
     * <p>Throws {@link vn.vtit.gemek.common.exception.AppException} with
     * {@link vn.vtit.gemek.common.exception.ErrorCode#NOT_FOUND} if no matching
     * notification is found for the given user.
     *
     * @param notificationId the UUID of the notification to mark as read.
     * @param userId         the owning user UUID — used to scope the update.
     */
    void markAsRead(UUID notificationId, UUID userId);

    /**
     * Marks all unread notifications for the given user as read.
     *
     * @param userId the UUID of the user whose notifications to mark as read.
     */
    void markAllAsRead(UUID userId);

    /**
     * Returns the count of unread notifications for the given user.
     *
     * @param userId the UUID of the user to count unread notifications for.
     * @return the number of unread notifications.
     */
    long countUnread(UUID userId);
}
