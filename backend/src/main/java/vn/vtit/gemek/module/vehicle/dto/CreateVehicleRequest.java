/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

import java.util.UUID;

/**
 * Request body for registering a new vehicle.
 *
 * <p>The referenced resident must exist and must be an active resident of the
 * referenced apartment. License plates must be globally unique.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateVehicleRequest {

    /** UUID of the resident registering the vehicle. Must not be {@code null}. */
    @NotNull(message = "residentId is required.")
    private UUID residentId;

    /** UUID of the apartment the vehicle is associated with. Must not be {@code null}. */
    @NotNull(message = "apartmentId is required.")
    private UUID apartmentId;

    /** Vehicle type. Must not be {@code null}. */
    @NotNull(message = "type is required.")
    private VehicleType type;

    /** License plate number. Must not be blank and must not exceed 20 characters. */
    @NotBlank(message = "licensePlate is required.")
    @Size(max = 20, message = "licensePlate must not exceed 20 characters.")
    private String licensePlate;

    /** Optional vehicle brand. */
    private String brand;

    /** Optional vehicle model. */
    private String model;

    /** Optional vehicle colour. */
    private String color;

    /** Optional notes. */
    private String notes;
}
