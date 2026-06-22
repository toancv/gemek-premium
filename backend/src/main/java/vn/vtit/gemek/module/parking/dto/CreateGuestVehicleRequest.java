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

import java.util.UUID;

/**
 * Request DTO for logging a guest vehicle entry.
 *
 * <p>Entry time defaults to server NOW() if not recorded; it is set by the
 * {@code @PrePersist} callback on the entity.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateGuestVehicleRequest {

    /** License plate of the visiting vehicle. */
    @NotBlank(message = "License plate is required.")
    @Size(max = 20, message = "License plate must not exceed 20 characters.")
    private String licensePlate;

    /** Name of the vehicle owner or driver. Optional. */
    @Size(max = 255, message = "Owner name must not exceed 255 characters.")
    private String ownerName;

    /** UUID of the apartment being visited. */
    @NotNull(message = "Host apartment ID is required.")
    private UUID hostApartmentId;

    /** Purpose of the visit. Optional. */
    @Size(max = 255, message = "Purpose must not exceed 255 characters.")
    private String purpose;

    /** Optional notes about the visit. */
    private String notes;
}
