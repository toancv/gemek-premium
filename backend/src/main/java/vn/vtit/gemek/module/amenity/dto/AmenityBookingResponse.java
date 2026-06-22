/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.dto;

import lombok.Builder;
import lombok.Getter;
import vn.vtit.gemek.module.amenity.entity.BookingStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing a single amenity booking.
 *
 * <p>Nested reference objects ({@link AmenityRef}, {@link ResidentRef}, {@link ApartmentRef},
 * {@link ApproverRef}) carry only the fields required by the API contract to avoid over-fetching.
 */
@Getter
@Builder
public class AmenityBookingResponse {

    /** Booking unique identifier. */
    private final UUID id;

    /** Slim reference to the booked amenity. */
    private final AmenityRef amenity;

    /** Slim reference to the resident who created the booking. */
    private final ResidentRef resident;

    /** Slim reference to the resident's apartment at booking time. */
    private final ApartmentRef apartment;

    /** Calendar date of the booking. */
    private final LocalDate bookingDate;

    /** Slot start time. */
    private final LocalTime startTime;

    /** Slot end time. */
    private final LocalTime endTime;

    /** Current lifecycle status. */
    private final BookingStatus status;

    /** Optional notes from the resident. */
    private final String notes;

    /** Rejection reason set by admin when status is REJECTED. */
    private final String rejectionReason;

    /** Admin who approved or rejected; {@code null} for auto-approved bookings. */
    private final ApproverRef approvedBy;

    /** Timestamp of admin approval or rejection action. */
    private final OffsetDateTime approvedAt;

    /** Record creation timestamp. */
    private final OffsetDateTime createdAt;

    // -------------------------------------------------------------------------
    // Nested reference types
    // -------------------------------------------------------------------------

    /**
     * Slim amenity reference embedded in booking responses.
     */
    @Getter
    @Builder
    public static class AmenityRef {

        /** Amenity UUID. */
        private final UUID id;

        /** Amenity display name. */
        private final String name;
    }

    /**
     * Slim resident reference embedded in booking responses.
     * Includes a nested user sub-reference carrying the full name.
     */
    @Getter
    @Builder
    public static class ResidentRef {

        /** Resident UUID. */
        private final UUID id;

        /** Nested user reference. */
        private final UserRef user;

        /**
         * User sub-reference within the resident reference.
         */
        @Getter
        @Builder
        public static class UserRef {

            /** User UUID. */
            private final UUID id;

            /** User full name. */
            private final String fullName;
        }
    }

    /**
     * Slim apartment reference embedded in booking responses.
     */
    @Getter
    @Builder
    public static class ApartmentRef {

        /** Apartment UUID. */
        private final UUID id;

        /** Apartment unit number (e.g., "A301"). */
        private final String unitNumber;
    }

    /**
     * Slim approver (user) reference embedded in booking responses.
     */
    @Getter
    @Builder
    public static class ApproverRef {

        /** User UUID. */
        private final UUID id;

        /** User full name. */
        private final String fullName;
    }
}
