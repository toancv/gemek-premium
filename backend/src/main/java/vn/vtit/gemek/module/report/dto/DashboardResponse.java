/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report.dto;

import java.util.Map;

/**
 * Response DTO for the dashboard KPI endpoint ({@code GET /api/reports/dashboard}).
 *
 * <p>Groups summary metrics from four subsystems: apartments, tickets, amenity bookings,
 * and contracts. All counts are computed at query time — no caching applied.
 */
public record DashboardResponse(
        ApartmentStats apartments,
        TicketStats tickets,
        AmenityStats amenities,
        ContractStats contracts
) {

    /**
     * KPIs for the apartment inventory.
     *
     * @param total         total number of apartments.
     * @param occupied      apartments with status OCCUPIED.
     * @param available     apartments with status AVAILABLE.
     * @param maintenance   apartments with status MAINTENANCE.
     * @param occupancyRate occupied / total, or 0 when total is 0.
     */
    public record ApartmentStats(
            long total,
            long occupied,
            long available,
            long maintenance,
            double occupancyRate
    ) {}

    /**
     * KPIs for the ticket subsystem.
     *
     * @param openRequests                 tickets with status NEW.
     * @param inProgressRequests           tickets with status IN_PROGRESS.
     * @param overdueRequests              tickets past their SLA deadline and not resolved.
     * @param avgResolutionHoursLast30Days average hours from creation to completion in last 30 days.
     * @param byCategory                   count of open+in-progress tickets keyed by category name.
     */
    public record TicketStats(
            long openRequests,
            long inProgressRequests,
            long overdueRequests,
            double avgResolutionHoursLast30Days,
            Map<String, Long> byCategory
    ) {}

    /**
     * KPIs for amenity bookings.
     *
     * @param bookingsThisMonth total bookings created in the current calendar month.
     * @param pendingApproval   bookings currently in PENDING status.
     */
    public record AmenityStats(
            long bookingsThisMonth,
            long pendingApproval
    ) {}

    /**
     * KPIs for service contracts.
     *
     * @param active           contracts with status ACTIVE.
     * @param expiringIn30Days ACTIVE contracts whose end date falls within 30 days.
     * @param expiringIn90Days ACTIVE contracts whose end date falls within 90 days.
     */
    public record ContractStats(
            long active,
            long expiringIn30Days,
            long expiringIn90Days
    ) {}
}
