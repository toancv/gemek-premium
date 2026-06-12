/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification;

import vn.vtit.gemek.module.notification.entity.SubscriptionJoinedVia;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for notification-thread membership (design §B, B1).
 *
 * <p>All operations are idempotent: double subscribe and unsubscribe of a
 * non-existent row are both no-ops, never errors — callers in dispatch paths
 * (P3) and the follow endpoint (P5) need no pre-checks.
 */
public interface SubscriptionService {

    /**
     * Adds the user to the entity's notification thread. Idempotent and
     * race-safe: if a row already exists (including one created concurrently),
     * the call is a silent no-op and the existing {@code joinedVia} is kept.
     *
     * @param userId     the user UUID.
     * @param entityType the entity-type label (e.g. "Ticket").
     * @param entityId   the entity UUID.
     * @param joinedVia  how the user joins the thread.
     */
    void subscribe(UUID userId, String entityType, UUID entityId, SubscriptionJoinedVia joinedVia);

    /**
     * Removes the user from the entity's notification thread. Idempotent:
     * removing a non-existent subscription is a no-op.
     *
     * @param userId     the user UUID.
     * @param entityType the entity-type label.
     * @param entityId   the entity UUID.
     */
    void unsubscribe(UUID userId, String entityType, UUID entityId);

    /**
     * Returns the user IDs of all participants of the entity's thread.
     *
     * @param entityType the entity-type label.
     * @param entityId   the entity UUID.
     * @return participant user UUIDs; empty when the thread has no members.
     */
    List<UUID> participantUserIds(String entityType, UUID entityId);

    /**
     * Returns whether the user has a FOLLOWER subscription row on the entity.
     *
     * <p>CREATOR/ASSIGNEE rows do not count — the flag drives the resident
     * follow/unfollow button, which only applies to opt-in followers (N3 P7).
     *
     * @param userId     the user UUID.
     * @param entityType the entity-type label.
     * @param entityId   the entity UUID.
     * @return whether a FOLLOWER row exists.
     */
    boolean isFollower(UUID userId, String entityType, UUID entityId);
}
