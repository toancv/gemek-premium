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
import java.util.UUID;

/**
 * Request body for {@code POST /api/apartments} (ADMIN only).
 *
 * @param blockId    UUID of the block this apartment belongs to, required.
 * @param floor      floor number, must be zero or positive.
 * @param unitNumber unit number unique within the block (e.g., "A301"), required.
 * @param areaSqm    floor area in square metres, optional.
 * @param notes      optional free-text notes.
 */
public record CreateApartmentRequest(

        @NotNull(message = "Block ID is required.")
        UUID blockId,

        @NotNull(message = "Floor is required.")
        @Min(value = 0, message = "Floor must be zero or positive.")
        Short floor,

        @NotBlank(message = "Unit number is required.")
        @Size(max = 20, message = "Unit number must not exceed 20 characters.")
        String unitNumber,

        BigDecimal areaSqm,

        String notes
) {}
