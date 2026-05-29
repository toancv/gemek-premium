/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

/**
 * Request body for updating mutable fields of an existing vehicle record.
 *
 * <p>All fields are optional. A {@code null} value means no change for that field.
 * If {@code licensePlate} is provided it must not duplicate another vehicle's plate.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVehicleRequest {

    /** New vehicle type. When {@code null} the current type is unchanged. */
    private VehicleType type;

    /**
     * New license plate.
     * When non-{@code null} and different from the current plate, a uniqueness
     * check is performed excluding the current vehicle record.
     */
    @Size(max = 20, message = "licensePlate must not exceed 20 characters.")
    private String licensePlate;

    /** New vehicle brand. When {@code null} the current value is unchanged. */
    private String brand;

    /** New vehicle model. When {@code null} the current value is unchanged. */
    private String model;

    /** New vehicle colour. When {@code null} the current value is unchanged. */
    private String color;

    /** New notes. When {@code null} the current value is unchanged. */
    private String notes;
}
