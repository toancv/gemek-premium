/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.parking.entity.ParkingSlotStatus;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;

/**
 * Request DTO for updating an existing parking slot.
 *
 * <p>All fields are optional — only non-null values are applied.
 * {@code slotNumber} may be changed provided the new value does not collide
 * with another existing slot.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateParkingSlotRequest {

    /**
     * New slot number. Optional; must be unique if supplied.
     */
    @Size(max = 20, message = "Slot number must not exceed 20 characters.")
    private String slotNumber;

    /** New zone label. Optional. */
    @Size(max = 50, message = "Zone must not exceed 50 characters.")
    private String zone;

    /** New vehicle type designation. Optional. */
    private ParkingSlotType type;

    /** New status. Optional — use with care; prefer assign/unassign endpoints for occupancy changes. */
    private ParkingSlotStatus status;

    /** Updated notes. Optional. */
    private String notes;
}
