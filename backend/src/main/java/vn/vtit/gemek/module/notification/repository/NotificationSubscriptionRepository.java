/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vtit.gemek.module.notification.entity.NotificationSubscription;
import vn.vtit.gemek.module.notification.entity.SubscriptionJoinedVia;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link NotificationSubscription} entities.
 */
public interface NotificationSubscriptionRepository
        extends JpaRepository<NotificationSubscription, UUID> {

    /**
     * Returns whether the user already has a subscription row for the entity.
     *
     * @param userId     the user UUID.
     * @param entityType the entity-type label (e.g. "Ticket").
     * @param entityId   the entity UUID.
     * @return {@code true} when a row exists.
     */
    boolean existsByUserIdAndEntityTypeAndEntityId(UUID userId, String entityType, UUID entityId);

    /**
     * Deletes the user's subscription row for the entity, if present.
     *
     * @param userId     the user UUID.
     * @param entityType the entity-type label.
     * @param entityId   the entity UUID.
     * @return number of rows deleted (0 or 1).
     */
    long deleteByUserIdAndEntityTypeAndEntityId(UUID userId, String entityType, UUID entityId);

    /**
     * Returns the user IDs of all thread participants of one entity.
     *
     * <p>ID projection — no entity hydration; same style as
     * {@code ResidentRepository.findRecipientUserIdsByScopeName}.
     *
     * @param entityType the entity-type label.
     * @param entityId   the entity UUID.
     * @return participant user UUIDs.
     */
    @Query("""
            SELECT s.user.id FROM NotificationSubscription s
            WHERE s.entityType = :entityType AND s.entityId = :entityId
            """)
    List<UUID> findParticipantUserIds(@Param("entityType") String entityType,
                                      @Param("entityId") UUID entityId);

    /**
     * Race-safe idempotent insert: a concurrent duplicate subscribe hits the
     * unique constraint and is silently ignored instead of raising — keeping
     * the surrounding transaction usable (an in-TX unique violation caught in
     * Java would still mark the TX rollback-only).
     *
     * @param userId     the user UUID.
     * @param entityType the entity-type label.
     * @param entityId   the entity UUID.
     * @param joinedVia  membership origin name (CREATOR/ASSIGNEE/FOLLOWER).
     * @return number of rows inserted (0 when the row already existed).
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_subscriptions (user_id, entity_type, entity_id, joined_via)
            VALUES (:userId, :entityType, :entityId, :joinedVia)
            ON CONFLICT (user_id, entity_type, entity_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId,
                       @Param("entityType") String entityType,
                       @Param("entityId") UUID entityId,
                       @Param("joinedVia") String joinedVia);

    /**
     * Returns whether a subscription row with the given membership origin exists.
     *
     * <p>Used for the {@code isFollowing} viewer flag (N3 P7) — only FOLLOWER rows
     * count; a CREATOR/ASSIGNEE row must not render an «Bỏ theo dõi» button.
     *
     * @param userId     the user UUID.
     * @param entityType the entity-type label.
     * @param entityId   the entity UUID.
     * @param joinedVia  the membership origin to match.
     * @return whether such a row exists.
     */
    boolean existsByUserIdAndEntityTypeAndEntityIdAndJoinedVia(
            UUID userId, String entityType, UUID entityId, SubscriptionJoinedVia joinedVia);
}
