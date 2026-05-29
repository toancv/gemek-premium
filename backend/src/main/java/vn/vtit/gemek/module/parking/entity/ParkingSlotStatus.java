/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.entity;

/**
 * Enumeration of possible parking slot occupancy states.
 *
 * <p>Maps to the PostgreSQL {@code parking_slot_status} ENUM defined in V1 migration.
 * Only an {@code AVAILABLE} slot may receive a new assignment.
 */
public enum ParkingSlotStatus {

    /** Slot is free and may be assigned to a vehicle. */
    AVAILABLE,

    /** Slot has an active assignment — a vehicle is currently assigned to it. */
    OCCUPIED,

    /** Slot is reserved for future use and cannot be freely assigned. */
    RESERVED
}
