/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single guest vehicle entry record.
 *
 * <p>Embeds minimal host apartment and recorded-by user references.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestVehicleResponse {

    /** Guest vehicle record UUID. */
    private UUID id;

    /** License plate of the visiting vehicle. */
    private String licensePlate;

    /** Name of the vehicle owner or driver. May be {@code null}. */
    private String ownerName;

    /** Minimal reference to the apartment being visited. */
    private ApartmentRef hostApartment;

    /** Timestamp when the guest vehicle entered the premises. */
    private OffsetDateTime entryTime;

    /**
     * Timestamp when the guest vehicle exited.
     * {@code null} while still on premises.
     */
    private OffsetDateTime exitTime;

    /** Purpose of the visit. May be {@code null}. */
    private String purpose;

    /** Staff member who logged the entry. May be {@code null}. */
    private UserRef recordedBy;

    /** Optional notes. May be {@code null}. */
    private String notes;

    /**
     * Minimal apartment reference embedded in the guest vehicle response.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApartmentRef {

        /** Apartment UUID. */
        private UUID id;

        /** Apartment unit number. */
        private String unitNumber;
    }

    /**
     * Minimal user reference for the staff member who recorded the entry.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRef {

        /** User UUID. */
        private UUID id;

        /** User's full name. */
        private String fullName;
    }
}
