/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code PUT /api/apartments/{id}} (ADMIN only).
 *
 * <p>Occupancy status is intentionally NOT a field here: AVAILABLE/OCCUPIED are fully derived
 * from active residents by {@code OccupancyResolver}, and MAINTENANCE has no set flow in the UI.
 * Accepting a client-supplied status would let an admin store a value that contradicts the
 * derived display — so status is not client-settable via update.
 *
 * @param floor      new floor number, must be zero or positive.
 * @param unitNumber new unit number, required.
 * @param areaSqm    new floor area in square metres, optional.
 * @param notes      optional free-text notes.
 */
public record UpdateApartmentRequest(

        @NotNull(message = "Floor is required.")
        @Min(value = 0, message = "Floor must be zero or positive.")
        Short floor,

        @NotBlank(message = "Unit number is required.")
        @Size(max = 20, message = "Unit number must not exceed 20 characters.")
        String unitNumber,

        BigDecimal areaSqm,

        String notes
) {}
