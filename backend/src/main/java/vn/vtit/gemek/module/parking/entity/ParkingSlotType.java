/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.entity;

/**
 * Enumeration of supported parking slot types.
 *
 * <p>Maps to the PostgreSQL {@code parking_slot_type} ENUM defined in V1 migration.
 */
public enum ParkingSlotType {

    /** Slot designated for passenger cars or SUVs. */
    CAR,

    /** Slot designated for motorbikes or scooters. */
    MOTORBIKE,

    /** Slot designated for bicycles. */
    BICYCLE
}
