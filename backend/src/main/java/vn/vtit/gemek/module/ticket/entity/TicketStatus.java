/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.entity;

/**
 * Enumeration of ticket lifecycle statuses, mapped to the PostgreSQL {@code ticket_status} ENUM type.
 */
public enum TicketStatus {
    /** Ticket submitted, not yet assigned to any staff or contractor. */
    NEW,
    /** Ticket assigned to a staff member or contractor; work not yet started. */
    ASSIGNED,
    /** Work is actively in progress. */
    IN_PROGRESS,
    /** Work completed successfully. */
    DONE,
    /** Ticket cancelled before completion. */
    CANCELLED
}
