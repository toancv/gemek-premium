/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.entity;

/**
 * Enumeration of possible apartment occupancy statuses.
 *
 * <p>Maps to the PostgreSQL {@code apartment_status} ENUM type defined in V1 migration.
 */
public enum ApartmentStatus {

    /** Apartment has no current residents and is available for assignment. */
    AVAILABLE,

    /** Apartment is currently occupied by at least one active resident. */
    OCCUPIED,

    /** Apartment is temporarily unavailable due to maintenance work. */
    MAINTENANCE
}
