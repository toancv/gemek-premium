/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.parking.entity.ParkingSlotStatus;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single parking slot record.
 *
 * <p>Returned by all slot read and write endpoints. Does not embed the current
 * assignment inline — callers that need assignment detail should query the assignments
 * list filtered by {@code slotId}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSlotResponse {

    /** Parking slot UUID. */
    private UUID id;

    /** Human-readable slot identifier (e.g., "B1-001"). */
    private String slotNumber;

    /** Zone or level label. May be {@code null}. */
    private String zone;

    /** Vehicle type this slot accommodates. */
    private ParkingSlotType type;

    /** Current occupancy status. */
    private ParkingSlotStatus status;

    /** Optional notes. May be {@code null}. */
    private String notes;

    /** Timestamp when this slot record was created. */
    private OffsetDateTime createdAt;
}
