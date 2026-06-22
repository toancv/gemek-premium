/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.resident.entity.ResidentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single resident history entry.
 *
 * <p>Returned in the paginated history endpoints for both resident-level
 * and apartment-level history queries.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidentHistoryResponse {

    /** History entry UUID. */
    private UUID id;

    /**
     * Event type string.
     * Valid values: MOVED_IN, MOVED_OUT, TYPE_CHANGED, PRIMARY_CONTACT_SET.
     */
    private String event;

    /** The date on which the event occurred. */
    private LocalDate eventDate;

    /** Resident type recorded at the time of the event. */
    private ResidentType type;

    /**
     * Reference to the staff member or admin who made the change.
     * May be {@code null} for system-initiated events.
     */
    private ChangedByRef changedBy;

    /** Notes recorded at the time of the event. */
    private String notes;

    /** Timestamp when this history record was created. */
    private OffsetDateTime createdAt;

    /**
     * Minimal reference to the user who performed the change.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangedByRef {

        /** User UUID. */
        private UUID id;

        /** User's full name. */
        private String fullName;
    }
}
