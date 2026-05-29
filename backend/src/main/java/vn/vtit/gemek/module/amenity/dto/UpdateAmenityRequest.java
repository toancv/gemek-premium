/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Request DTO for updating an existing amenity.
 *
 * <p>All fields are optional; only non-null values are applied.
 * Uniqueness of {@code name} (when provided) is checked against other records
 * in the service layer via {@code existsByNameAndIdNot}.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateAmenityRequest {

    /** New display name; must be unique if provided. */
    private String name;

    /** New description. */
    private String description;

    /** New physical location. */
    private String location;

    /** New capacity; must be positive if provided. */
    @Positive(message = "capacity must be positive")
    private Short capacity;

    /** New opening time. */
    private LocalTime openingTime;

    /** New closing time. */
    private LocalTime closingTime;

    /** New per-resident daily booking limit; must be at least 1 if provided. */
    @Min(value = 1, message = "maxDailyBookingsPerResident must be at least 1")
    private Short maxDailyBookingsPerResident;

    /** New approval requirement flag. */
    private Boolean requiresApproval;
}
