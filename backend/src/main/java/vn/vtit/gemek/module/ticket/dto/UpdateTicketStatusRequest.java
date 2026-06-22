/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;

/**
 * Request body for advancing or cancelling a ticket's status.
 *
 * <p>The service validates the transition against the allowed workflow graph
 * and raises {@code INVALID_STATUS_TRANSITION} for illegal moves.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketStatusRequest {

    /** Target status after the transition. */
    @NotNull(message = "status is required.")
    private TicketStatus status;

    /** Optional notes explaining the reason for the transition. */
    private String notes;

    /** Staff-provided notes recorded when the ticket is marked DONE. */
    private String resolutionNotes;
}
