/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vtit.gemek.module.notification.entity.Notification;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Notification} entity.
 *
 * <p>Provides paginated list access, unread count, and bulk/single mark-as-read
 * operations scoped to the owning user to prevent cross-user data access.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Returns a page of notifications for a given user, newest first.
     *
     * @param userId   the user UUID whose notifications to retrieve.
     * @param pageable pagination and sort parameters.
     * @return a page of {@link Notification} entities.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Counts unread notifications for a given user.
     *
     * @param userId the user UUID to count unread notifications for.
     * @return the count of unread notifications.
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.isRead = false")
    long countUnreadByUserId(@Param("userId") UUID userId);

    /**
     * Marks a single notification as read, scoped to the owning user.
     *
     * <p>Returns 0 if no matching unread notification is found, which signals
     * a NOT_FOUND condition to the service layer.
     *
     * @param id     the notification UUID to mark as read.
     * @param userId the owning user UUID — prevents cross-user modification.
     * @return the number of rows updated (0 or 1).
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.user.id = :userId")
    int markAsRead(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Marks all unread notifications for a user as read in a single update.
     *
     * @param userId the user UUID whose notifications to mark as read.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    void markAllAsRead(@Param("userId") UUID userId);
}
