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
 * Request body for the place-resident flow ({@code POST /api/residents}).
 *
 * <p>The server branches on {@code phone}: a brand-new phone provisions a user + residency atomically; a
 * phone that already belongs to an existing user REUSES that user (after {@code confirmReuse=true}) and only
 * adds a new residency — supporting move-in / return / concurrent multi-residency.
 *
 * <p><strong>Conditional validation:</strong> {@code fullName}, {@code password}, and {@code dateOfBirth}
 * carry no bean-validation constraints here because they are required ONLY for the NEW branch — which bean
 * validation cannot detect without a DB phone lookup. The service enforces them (presence + password
 * complexity) for the NEW branch; the reuse branch ignores all identity fields entirely (identity is
 * server-derived from the existing user, never overwritten by request values — IDOR-safe).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateResidentRequest {

    // -------------------------------------------------------------------------
    // User account fields (required only for the NEW branch — enforced in the service)
    // -------------------------------------------------------------------------

    /** Full display name of the new user (NEW branch only; ignored on reuse). */
    private String fullName;

    /** Optional informational email address. Unique when provided (NEW branch only; ignored on reuse). */
    @Email(message = "email must be a valid address.")
    private String email;

    /** Plain-text password — BCrypt-hashed before storage (NEW branch only; ignored on reuse). */
    private String password;

    /** Phone number — the login identifier and the branch key. Must not be blank. */
    @NotBlank(message = "phone is required.")
    private String phone;

    /** Date of birth of the new user (NEW branch only; ignored on reuse). */
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

    /**
     * Explicit admin confirmation to REUSE an existing user's profile when {@code phone} already belongs to
     * one. Defaults to {@code false}. Irrelevant for the NEW branch. When the phone matches an existing user
     * who is not active in the target apartment and this is {@code false}, the server returns
     * {@code 409 REUSE_CONFIRMATION_REQUIRED} (creating nothing) so the frontend can confirm; {@code true}
     * proceeds with reuse (+ reactivate if disabled) and adds the new residency.
     */
    private boolean confirmReuse;
}
