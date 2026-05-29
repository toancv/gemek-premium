/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.entity;

/**
 * Enumeration of ticket priority levels, mapped to the PostgreSQL {@code ticket_priority} ENUM type.
 */
public enum TicketPriority {
    /** Low priority — no urgency. */
    LOW,
    /** Medium priority — default for new tickets. */
    MEDIUM,
    /** High priority — requires prompt attention. */
    HIGH,
    /** Urgent — requires immediate action. */
    URGENT
}
