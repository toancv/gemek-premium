/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification.entity;

/**
 * Enumeration of how a user joined a notification thread.
 *
 * <p>Maps to the {@code joined_via VARCHAR(20)} column of
 * {@code notification_subscriptions} (V14), guarded by a CHECK constraint
 * with the same three values.
 */
public enum SubscriptionJoinedVia {

    /** The user created the underlying entity (e.g. submitted the ticket). */
    CREATOR,

    /** The user was assigned to work on the underlying entity. */
    ASSIGNEE,

    /** The user opted in via the follow endpoint. */
    FOLLOWER
}
