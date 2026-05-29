/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO returned for a single vehicle record.
 *
 * <p>Embeds minimal resident and apartment references so the caller does not
 * need additional lookups to display ownership context.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {

    /** Vehicle record UUID. */
    private UUID id;

    /** Minimal resident reference for the registered owner. */
    private ResidentRef resident;

    /** Minimal apartment reference. */
    private ApartmentRef apartment;

    /** Vehicle type — CAR, MOTORBIKE, BICYCLE, or OTHER. */
    private VehicleType type;

    /** License plate number. */
    private String licensePlate;

    /** Vehicle brand (e.g., Toyota). May be {@code null}. */
    private String brand;

    /** Vehicle model (e.g., Camry). May be {@code null}. */
    private String model;

    /** Vehicle colour. May be {@code null}. */
    private String color;

    /** Whether this vehicle registration is active. */
    private boolean isActive;

    /** Timestamp when this vehicle record was created. */
    private OffsetDateTime createdAt;

    /**
     * Minimal resident reference embedded in the vehicle response.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResidentRef {

        /** Resident record UUID. */
        private UUID id;

        /** Minimal user reference for the resident. */
        private UserRef user;

        /**
         * Minimal user reference nested inside the resident reference.
         */
        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UserRef {

            /** User's full name. */
            private String fullName;
        }
    }

    /**
     * Minimal apartment reference embedded in the vehicle response.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApartmentRef {

        /** Apartment UUID. */
        private UUID id;

        /** Apartment unit number within its block. */
        private String unitNumber;
    }
}
