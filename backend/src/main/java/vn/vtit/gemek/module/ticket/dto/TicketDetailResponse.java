/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vtit.gemek.module.ticket.entity.PhotoPhase;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full detail DTO returned by the single-ticket GET endpoint.
 *
 * <p>Includes all summary fields plus description, scheduling, rating comment,
 * resolution notes, photo list (with presigned URLs), and full status history.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailResponse {

    /** Unique ticket identifier. */
    private UUID id;

    /** Apartment reference. */
    private ApartmentRef apartment;

    /** User who submitted the ticket (includes phone for detail view). */
    private SubmitterRef submittedBy;

    /** Category of the request. */
    private TicketCategory category;

    /** Short title. */
    private String title;

    /** Full description of the issue. */
    private String description;

    /** Current lifecycle status. */
    private TicketStatus status;

    /** Priority level. */
    private TicketPriority priority;

    /** Staff member assigned to the ticket, or {@code null}. */
    private UserRef assignedToUser;

    /** Contractor assigned to the ticket, or {@code null}. */
    private ContractorRef assignedToContractor;

    /** Planned work date, or {@code null} if not scheduled. */
    private LocalDate scheduledDate;

    /** Completion timestamp, or {@code null} if not yet done. */
    private OffsetDateTime completedDate;

    /** SLA deadline timestamp, or {@code null} for SUGGESTION_FEEDBACK. */
    private OffsetDateTime slaDeadline;

    /**
     * Whether the SLA deadline has been breached.
     */
    private boolean slaBreached;

    /** Resident rating (1–5), or {@code null} if not yet rated. */
    private Short rating;

    /** Resident comment accompanying the rating, or {@code null}. */
    private String ratingComment;

    /** Staff-provided resolution notes, or {@code null}. */
    private String resolutionNotes;

    /** Creator-chosen community visibility flag (N3 P5). */
    private Boolean isPublic;

    /**
     * Whether this response was produced by the redacted public-view mapping (G8) —
     * {@code true} only for a resident outside the ticket's household viewing a
     * public ticket. The FE keys the follow button off this flag (N3 P7).
     */
    private boolean redacted;

    /**
     * Whether the calling resident has a FOLLOWER subscription row on this ticket.
     * Set only on the detail GET for RESIDENT callers; {@code null} elsewhere
     * (mutation responses and staff views have no follow semantics).
     */
    @Setter
    private Boolean isFollowing;

    /** Ordered list of photos attached to this ticket. */
    private List<PhotoResponse> photos;

    /** Full status transition history in chronological order. */
    private List<StatusHistoryResponse> statusHistory;

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
        /** Unit number within the block. */
        private String unitNumber;
        /** Block reference. */
        private BlockRef block;
    }

    /**
     * Compact block reference.
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
     * Submitter reference including phone number for the detail view.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitterRef {
        /** User UUID. */
        private UUID id;
        /** Display name. */
        private String fullName;
        /** Contact phone number. */
        private String phone;
    }

    /**
     * Compact user reference for assignee fields.
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
     * Compact contractor reference.
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

    /**
     * Photo attachment with a short-lived presigned download URL (10 minutes).
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoResponse {
        /** Photo UUID. */
        private UUID id;
        /** Work phase this photo documents. */
        private PhotoPhase phase;
        /** Presigned MinIO GET URL, valid for 10 minutes. */
        private String presignedUrl;
        /** Original file name. */
        private String fileName;
        /** MIME type. */
        private String mimeType;
        /** File size in bytes. */
        private Integer fileSizeBytes;
        /** Upload timestamp. */
        private OffsetDateTime uploadedAt;
    }

    /**
     * Single entry in the ticket's status transition history.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryResponse {
        /** History entry UUID. */
        private UUID id;
        /** Status before the transition; {@code null} for the initial NEW entry. */
        private TicketStatus oldStatus;
        /** Status after the transition. */
        private TicketStatus newStatus;
        /** User who triggered the transition, or {@code null}. */
        private UserRef changedBy;
        /** Optional notes explaining the transition. */
        private String notes;
        /** Timestamp of the transition. */
        private OffsetDateTime changedAt;
    }
}
