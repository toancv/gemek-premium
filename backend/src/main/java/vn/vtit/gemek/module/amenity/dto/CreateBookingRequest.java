/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Request DTO for creating a new amenity booking.
 *
 * <p>The resident is resolved server-side from the authenticated principal;
 * the client only provides the amenity, date, and desired time slot.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateBookingRequest {

    /** The amenity to be booked. */
    @NotNull(message = "amenityId is required")
    private UUID amenityId;

    /** The date on which the amenity is to be booked. */
    @NotNull(message = "bookingDate is required")
    private LocalDate bookingDate;

    /** Desired slot start time; must be within the amenity's opening hours. */
    @NotNull(message = "startTime is required")
    private LocalTime startTime;

    /** Desired slot end time; must be within the amenity's closing hours and after startTime. */
    @NotNull(message = "endTime is required")
    private LocalTime endTime;

    /** Optional free-text notes for the booking. */
    private String notes;
}
