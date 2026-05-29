/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for {@code GET /api/reports/amenity-usage}.
 *
 * <p>Provides per-amenity booking statistics for the requested period,
 * including peak day and a utilisation rate approximation.
 */
public record AmenityUsageReportResponse(
        Period period,
        List<AmenityRow> byAmenity
) {

    /**
     * Inclusive date range of the report.
     *
     * @param from start date (inclusive).
     * @param to   end date (inclusive).
     */
    public record Period(LocalDate from, LocalDate to) {}

    /**
     * Booking statistics for one amenity.
     *
     * @param amenity           summary identifying the amenity.
     * @param totalBookings     all bookings in the period regardless of status.
     * @param approvedBookings  bookings with status APPROVED or COMPLETED.
     * @param rejectedBookings  bookings with status REJECTED.
     * @param cancelledBookings bookings with status CANCELLED.
     * @param peakDay           the calendar date with the most bookings; {@code null} if none.
     * @param utilizationRate   approvedBookings / totalBookings; 0.0 when totalBookings is 0.
     */
    public record AmenityRow(
            AmenitySummary amenity,
            long totalBookings,
            long approvedBookings,
            long rejectedBookings,
            long cancelledBookings,
            LocalDate peakDay,
            double utilizationRate
    ) {}

    /**
     * Minimal amenity identifier used inside {@link AmenityRow}.
     *
     * @param id   amenity UUID.
     * @param name amenity display name.
     */
    public record AmenitySummary(UUID id, String name) {}
}
