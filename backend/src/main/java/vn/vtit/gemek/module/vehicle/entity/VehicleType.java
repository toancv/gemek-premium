/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle.entity;

/**
 * Enumeration of supported vehicle types.
 *
 * <p>Maps to the PostgreSQL {@code vehicle_type} ENUM type defined in V1 migration.
 */
public enum VehicleType {

    /** Passenger car or SUV. */
    CAR,

    /** Motorbike or scooter. */
    MOTORBIKE,

    /** Bicycle. */
    BICYCLE,

    /** Any other vehicle type not covered above. */
    OTHER
}
