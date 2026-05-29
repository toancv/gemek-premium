/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;

import java.util.UUID;

/**
 * Request body for creating a new ticket.
 *
 * <p>RESIDENT callers must supply their own apartment ID; the service enforces
 * that they may only submit for their active apartment.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {

    /** UUID of the apartment the ticket is raised for. */
    @NotNull(message = "apartmentId is required.")
    private UUID apartmentId;

    /** Category of the request. */
    @NotNull(message = "category is required.")
    private TicketCategory category;

    /** Short title describing the issue. */
    @NotBlank(message = "title is required.")
    @Size(max = 255, message = "title must not exceed 255 characters.")
    private String title;

    /** Optional detailed description. */
    private String description;

    /**
     * Priority level. Defaults to {@link TicketPriority#MEDIUM} when not supplied.
     */
    @Builder.Default
    private TicketPriority priority = TicketPriority.MEDIUM;
}
