/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.resident.entity.ResidentType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating a new resident record together with a new user account.
 *
 * <p>User account fields (fullName, email, password) and resident fields are submitted
 * in a single request. The service creates the user and resident atomically in one
 * transaction — no orphan user is ever left without a corresponding resident.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateResidentRequest {

    // -------------------------------------------------------------------------
    // User account fields
    // -------------------------------------------------------------------------

    /** Full display name of the new user. Must not be blank. */
    @NotBlank(message = "fullName is required.")
    private String fullName;

    /** Email address for the new user account. Must be unique and valid. */
    @NotBlank(message = "email is required.")
    @Email(message = "email must be a valid address.")
    private String email;

    /** Plain-text password — will be BCrypt-hashed before storage. Must not be blank. */
    @NotBlank(message = "password is required.")
    private String password;

    /** Optional phone number. */
    private String phone;

    /** Optional date of birth. */
    private LocalDate dateOfBirth;

    // -------------------------------------------------------------------------
    // Resident record fields
    // -------------------------------------------------------------------------

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
    @JsonProperty("isPrimaryContact")
    private boolean isPrimaryContact;

    /** Optional free-text notes. */
    private String notes;
}
