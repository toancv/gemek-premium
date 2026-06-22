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
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the amenity availability calendar endpoint.
 *
 * <p>Returns the amenity's operating hours and all currently booked (PENDING or APPROVED)
 * time slots for the requested date, allowing clients to render a visual availability grid.
 */
@Getter
@Builder
public class AvailabilityResponse {

    /** The amenity UUID this response is for. */
    private final UUID amenityId;

    /** The date for which availability is reported. */
    private final LocalDate date;

    /** Amenity opening time on the requested date. */
    private final LocalTime openingTime;

    /** Amenity closing time on the requested date. */
    private final LocalTime closingTime;

    /** All currently active (PENDING or APPROVED) booked slots on this date. */
    private final List<SlotInfo> bookedSlots;

    /**
     * A single booked time slot within the availability response.
     *
     * <p>Carries the start and end times plus the booking status so the client
     * can visually distinguish pending from confirmed slots.
     */
    @Getter
    @Builder
    public static class SlotInfo {

        /** Start time of this booked slot. */
        private final LocalTime start;

        /** End time of this booked slot. */
        private final LocalTime end;

        /** Current status of the booking occupying this slot. */
        private final BookingStatus status;
    }
}
