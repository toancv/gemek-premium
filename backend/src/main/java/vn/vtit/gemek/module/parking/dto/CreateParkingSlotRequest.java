/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;

/**
 * Request DTO for creating a new parking slot.
 *
 * <p>New slots are always created with status {@code AVAILABLE}; the status field
 * is not part of the creation request.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateParkingSlotRequest {

    /**
     * Human-readable slot identifier (e.g., "B1-001").
     * Must be unique across all parking slots.
     */
    @NotBlank(message = "Slot number is required.")
    @Size(max = 20, message = "Slot number must not exceed 20 characters.")
    private String slotNumber;

    /** Optional zone or level label (e.g., "B1", "Level 2"). */
    @Size(max = 50, message = "Zone must not exceed 50 characters.")
    private String zone;

    /** Vehicle type this slot is designated for. */
    @NotNull(message = "Slot type is required.")
    private ParkingSlotType type;

    /** Optional notes about the slot. */
    private String notes;
}
