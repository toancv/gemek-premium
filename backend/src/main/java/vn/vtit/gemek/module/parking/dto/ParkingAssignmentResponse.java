/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single parking assignment record.
 *
 * <p>Embeds minimal slot, vehicle, and apartment references so callers can display
 * context without additional lookups.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingAssignmentResponse {

    /** Assignment record UUID. */
    private UUID id;

    /** Minimal slot reference. */
    private SlotRef slot;

    /** Minimal vehicle reference. */
    private VehicleRef vehicle;

    /** Minimal apartment reference. */
    private ApartmentRef apartment;

    /** Date from which the assignment is effective. */
    private LocalDate startDate;

    /**
     * Date on which the assignment ended.
     * {@code null} if the assignment is currently active.
     */
    private LocalDate endDate;

    /** Parking card or access tag number. May be {@code null}. */
    private String parkingCardNumber;

    /** Optional notes. May be {@code null}. */
    private String notes;

    /** Timestamp when this assignment record was created. */
    private OffsetDateTime createdAt;

    /**
     * Minimal parking slot reference embedded in the assignment response.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotRef {

        /** Slot UUID. */
        private UUID id;

        /** Human-readable slot identifier. */
        private String slotNumber;

        /** Zone label. May be {@code null}. */
        private String zone;

        /** Vehicle type the slot accommodates. */
        private ParkingSlotType type;
    }

    /**
     * Minimal vehicle reference embedded in the assignment response.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleRef {

        /** Vehicle UUID. */
        private UUID id;

        /** License plate number. */
        private String licensePlate;

        /** Vehicle type. */
        private VehicleType type;
    }

    /**
     * Minimal apartment reference embedded in the assignment response.
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
