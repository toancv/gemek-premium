/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for assigning a parking slot to a vehicle.
 *
 * <p>The {@code parkingSlotId} is also supplied as a path variable on the
 * {@code POST /api/parking/slots/{id}/assign} endpoint; the value in this body
 * is used for the service call and must match the path variable.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssignmentRequest {

    /** UUID of the parking slot to assign. */
    @NotNull(message = "Parking slot ID is required.")
    private UUID parkingSlotId;

    /** UUID of the vehicle being assigned to the slot. */
    @NotNull(message = "Vehicle ID is required.")
    private UUID vehicleId;

    /** UUID of the apartment whose resident is using this slot. */
    @NotNull(message = "Apartment ID is required.")
    private UUID apartmentId;

    /** Date from which the assignment is effective. */
    @NotNull(message = "Start date is required.")
    private LocalDate startDate;

    /** Optional parking card or access tag number. */
    private String parkingCardNumber;

    /** Optional notes about the assignment. */
    private String notes;
}
