/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Request DTO for creating a new amenity.
 *
 * <p>Validated via Jakarta Bean Validation on the controller layer.
 * {@code name} must be unique across all amenities (checked in the service layer).
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateAmenityRequest {

    /** Display name; must be non-blank and unique. */
    @NotBlank(message = "name is required")
    private String name;

    /** Optional long-form description. */
    private String description;

    /** Optional physical location within the building. */
    private String location;

    /** Optional positive capacity; {@code null} means unconstrained. */
    @Positive(message = "capacity must be positive")
    private Short capacity;

    /** Time from which bookings may start; defaults to 06:00 if not provided. */
    @NotNull(message = "openingTime is required")
    private LocalTime openingTime;

    /** Time by which all bookings must end; defaults to 22:00 if not provided. */
    @NotNull(message = "closingTime is required")
    private LocalTime closingTime;

    /** Maximum bookings a resident may place for this amenity on a single day. */
    @Min(value = 1, message = "maxDailyBookingsPerResident must be at least 1")
    private short maxDailyBookingsPerResident = 1;

    /** Whether bookings require explicit admin approval. Defaults to {@code false}. */
    private boolean requiresApproval = false;
}
