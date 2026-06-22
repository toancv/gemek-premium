/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.notification.entity;

/**
 * Enumeration of in-app notification types.
 *
 * <p>Maps to the PostgreSQL {@code notification_type} ENUM defined in V1 migration.
 * Each constant represents a specific triggering event in the system.
 */
public enum NotificationType {

    /** A new support ticket has been created. */
    TICKET_CREATED,

    /** A support ticket has been assigned to a technician. */
    TICKET_ASSIGNED,

    /** A support ticket's status has changed. */
    TICKET_STATUS_CHANGED,

    /** A support ticket has received a resident rating. */
    TICKET_RATED,

    /** A completed support ticket awaits the submitter's rating. */
    TICKET_RATING_REQUESTED,

    /** A support ticket is approaching its SLA deadline. */
    TICKET_SLA_WARNING,

    /** A support ticket has breached its SLA deadline. */
    TICKET_SLA_BREACHED,

    /** An amenity booking has been approved. */
    BOOKING_APPROVED,

    /** An amenity booking has been rejected. */
    BOOKING_REJECTED,

    /** A reminder for an upcoming amenity booking. */
    BOOKING_REMINDER,

    /** A new announcement has been published. */
    ANNOUNCEMENT_PUBLISHED,

    /** A new resident has been added to the household's apartment. */
    HOUSEHOLD_MEMBER_ADDED,

    /** A contractor contract is approaching its expiry date. */
    CONTRACT_EXPIRING,

    /** A maintenance schedule task is due or overdue. */
    SCHEDULE_DUE,

    /** A general-purpose notification. */
    GENERAL
}
