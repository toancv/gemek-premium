/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.entity;

/**
 * Enumeration of ticket categories, mapped to the PostgreSQL {@code ticket_category} ENUM type.
 */
public enum TicketCategory {
    /** Physical maintenance or repair work — the only category eligible for contractor assignment. */
    MAINTENANCE_REPAIR,
    /** Resident complaint about a service, neighbour, or facility. */
    COMPLAINT,
    /** Administrative request (document issuance, lease queries, etc.). */
    ADMINISTRATIVE,
    /** Resident suggestion or general feedback. No SLA applies. */
    SUGGESTION_FEEDBACK,
    /** Anything that does not fit the above categories. */
    OTHER
}
