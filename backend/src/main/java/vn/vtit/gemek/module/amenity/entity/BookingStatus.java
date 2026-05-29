/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.entity;

/**
 * Lifecycle statuses for an amenity booking.
 *
 * <p>Maps to the PostgreSQL {@code booking_status} ENUM defined in V1 migration.
 * Valid transitions are documented in API-SPEC.md Appendix A.
 *
 * <ul>
 *   <li>{@link #PENDING}   — submitted, awaiting admin approval.</li>
 *   <li>{@link #APPROVED}  — approved by admin, or auto-approved when {@code requiresApproval=false}.</li>
 *   <li>{@link #REJECTED}  — rejected by admin (terminal).</li>
 *   <li>{@link #CANCELLED} — cancelled by resident or admin (terminal).</li>
 *   <li>{@link #COMPLETED} — booking window elapsed; set by {@code BookingCompletionScheduler} (terminal).</li>
 * </ul>
 */
public enum BookingStatus {

    /** Submitted and awaiting admin decision. */
    PENDING,

    /** Approved — either manually or auto-approved. */
    APPROVED,

    /** Rejected by admin; no further transitions allowed. */
    REJECTED,

    /** Cancelled by resident (before booking_date) or by admin. */
    CANCELLED,

    /** Booking window has passed; set by the completion scheduler. */
    COMPLETED
}
