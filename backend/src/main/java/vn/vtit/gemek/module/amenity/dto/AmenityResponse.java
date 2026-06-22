/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a single amenity resource.
 *
 * <p>Returned by all amenity read and write endpoints. Time fields use
 * {@link LocalTime} and are serialized as {@code "HH:mm"} strings.
 */
@Getter
@Builder
public class AmenityResponse {

    /** Unique amenity identifier. */
    private final UUID id;

    /** Display name of the amenity. */
    private final String name;

    /** Optional description of the amenity. */
    private final String description;

    /** Physical location within the building. */
    private final String location;

    /** Maximum simultaneous occupancy; {@code null} if unconstrained. */
    private final Short capacity;

    /** Time from which bookings may start each day. */
    private final LocalTime openingTime;

    /** Time by which all bookings must end each day. */
    private final LocalTime closingTime;

    /** Maximum bookings per resident per day for this amenity. */
    private final short maxDailyBookingsPerResident;

    /** Whether bookings require admin approval before becoming active. */
    private final boolean requiresApproval;

    /** Whether this amenity is currently active and accepting bookings. */
    private final boolean active;

    /** Timestamp when this amenity record was created. */
    private final OffsetDateTime createdAt;
}
