/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO returned in paginated ticket list responses.
 *
 * <p>Contains enough information to render a list row without loading photos or history.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryResponse {

    /** Unique ticket identifier. */
    private UUID id;

    /** Apartment reference. */
    private ApartmentRef apartment;

    /** User who submitted the ticket. */
    private UserRef submittedBy;

    /** Category of the request. */
    private TicketCategory category;

    /** Short title. */
    private String title;

    /** Current lifecycle status. */
    private TicketStatus status;

    /** Priority level. */
    private TicketPriority priority;

    /** Staff member assigned to the ticket, or {@code null}. */
    private UserRef assignedToUser;

    /** Contractor assigned to the ticket, or {@code null}. */
    private ContractorRef assignedToContractor;

    /** SLA deadline timestamp, or {@code null} for SUGGESTION_FEEDBACK tickets. */
    private OffsetDateTime slaDeadline;

    /**
     * Whether the SLA deadline has been breached.
     * {@code true} when {@code slaDeadline} is non-null, in the past, and
     * the ticket is not DONE or CANCELLED.
     */
    private boolean slaBreached;

    /** Resident rating (1–5) after completion, or {@code null} if not yet rated. */
    private Short rating;

    /** Record creation timestamp. */
    private OffsetDateTime createdAt;

    /** Record last-modified timestamp. */
    private OffsetDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Inner reference types
    // -------------------------------------------------------------------------

    /**
     * Compact apartment reference used inside ticket responses.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApartmentRef {
        /** Apartment UUID. */
        private UUID id;
        /** Unit number within the block (e.g. "A301"). */
        private String unitNumber;
        /** Block reference. */
        private BlockRef block;
    }

    /**
     * Compact block reference used inside apartment references.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockRef {
        /** Block name. */
        private String name;
    }

    /**
     * Compact user reference used inside ticket responses.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRef {
        /** User UUID. */
        private UUID id;
        /** Display name. */
        private String fullName;
    }

    /**
     * Compact contractor reference used inside ticket responses.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractorRef {
        /** Contractor UUID. */
        private UUID id;
        /** Company name. */
        private String companyName;
    }
}
