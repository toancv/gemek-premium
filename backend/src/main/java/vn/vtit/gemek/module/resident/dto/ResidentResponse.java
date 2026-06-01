/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.resident.entity.ResidentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO returned for a single resident record.
 *
 * <p>Embeds minimal user and apartment references to avoid requiring the caller
 * to make additional lookups.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidentResponse {

    /** Resident record UUID. */
    private UUID id;

    /** Minimal user reference for the resident. */
    private UserRef user;

    /** Minimal apartment reference for the resident. */
    private ApartmentRef apartment;

    /** Resident type — OWNER or TENANT. */
    private ResidentType type;

    /** Date the resident moved in. */
    private LocalDate moveInDate;

    /**
     * Date the resident moved out.
     * {@code null} means the resident is currently active.
     */
    private LocalDate moveOutDate;

    /** Whether this resident is the primary contact for the apartment. */
    @JsonProperty("isPrimaryContact")
    private boolean isPrimaryContact;

    /** Optional notes. */
    private String notes;

    /** Timestamp when this resident record was created. */
    private OffsetDateTime createdAt;

    /**
     * Minimal user reference embedded in the resident response.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRef {

        /** User UUID. */
        private UUID id;

        /** User's full name. */
        private String fullName;

        /** User's email address. */
        private String email;

        /** User's phone number. */
        private String phone;
    }

    /**
     * Minimal apartment reference embedded in the resident response.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApartmentRef {

        /** Apartment UUID. */
        private UUID id;

        /** Apartment unit number within its block. */
        private String unitNumber;
    }
}
