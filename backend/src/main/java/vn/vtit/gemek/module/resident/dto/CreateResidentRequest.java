/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.resident.entity.ResidentType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating a new resident record.
 *
 * <p>The referenced user must exist and must not already be an active resident.
 * The referenced apartment must exist.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateResidentRequest {

    /** UUID of the user to assign as a resident. Must not be {@code null}. */
    @NotNull(message = "userId is required.")
    private UUID userId;

    /** UUID of the apartment to assign the resident to. Must not be {@code null}. */
    @NotNull(message = "apartmentId is required.")
    private UUID apartmentId;

    /** Resident type — OWNER or TENANT. Must not be {@code null}. */
    @NotNull(message = "type is required.")
    private ResidentType type;

    /** Date the resident moves in. Must not be {@code null}. */
    @NotNull(message = "moveInDate is required.")
    private LocalDate moveInDate;

    /**
     * Whether this resident should be set as the primary contact for the apartment.
     * Defaults to {@code false} when not provided.
     */
    private boolean isPrimaryContact;

    /** Optional free-text notes. */
    private String notes;
}
