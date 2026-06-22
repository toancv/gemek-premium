/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for assigning a ticket to a staff member or contractor.
 *
 * <p>Exactly one of {@code assignedToUserId} and {@code assignedToContractorId}
 * must be non-null. The service enforces this constraint and raises a
 * {@code VALIDATION_ERROR} if both are supplied simultaneously.
 * Contractor assignment is only permitted when the ticket category is
 * {@code MAINTENANCE_REPAIR}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignTicketRequest {

    /** UUID of the staff user to assign. Mutually exclusive with {@code assignedToContractorId}. */
    private UUID assignedToUserId;

    /**
     * UUID of the contractor to assign.
     * Mutually exclusive with {@code assignedToUserId}.
     * Only valid for {@code MAINTENANCE_REPAIR} tickets.
     */
    private UUID assignedToContractorId;

    /** Planned date for the work. Optional. */
    private LocalDate scheduledDate;

    /** Optional notes about the assignment. */
    private String notes;

    /**
     * Validates that at least one assignee is specified.
     *
     * <p>The service also validates that both are not set simultaneously; this
     * constraint ensures the request is not completely empty of assignees.
     *
     * @return {@code true} when at least one of the assignee fields is non-null.
     */
    // SECURITY-FIX: cross-field validation ensures at least one assignee is provided
    @AssertTrue(message = "At least one assignee (user or contractor) must be specified.")
    public boolean isAssigneePresent() {
        return assignedToUserId != null || assignedToContractorId != null;
    }
}
