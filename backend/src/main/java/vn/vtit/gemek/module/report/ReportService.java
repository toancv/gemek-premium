/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report;

import vn.vtit.gemek.module.report.dto.AmenityUsageReportResponse;
import vn.vtit.gemek.module.report.dto.ContractsExpiringResponse;
import vn.vtit.gemek.module.report.dto.DashboardResponse;
import vn.vtit.gemek.module.report.dto.ResidentReportResponse;
import vn.vtit.gemek.module.report.dto.TicketReportResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service contract for the Reports and Dashboard module.
 *
 * <p>All methods are read-only aggregations across multiple modules.
 * Callers must hold ADMIN or BOARD_MEMBER role — enforcement is at the controller layer.
 */
public interface ReportService {

    /**
     * Returns summary KPIs for the dashboard landing page.
     *
     * @return dashboard response with apartment, ticket, amenity, and contract stats.
     */
    DashboardResponse getDashboard();

    /**
     * Returns aggregated ticket statistics for the given period and optional filters.
     *
     * @param from        optional start date (inclusive); {@code null} means no lower bound.
     * @param to          optional end date (inclusive); {@code null} means no upper bound.
     * @param groupBy     breakdown dimension: {@code "month"}, {@code "category"},
     *                    {@code "status"}, or {@code "assignee"}.
     * @param category    optional ticket category filter.
     * @param apartmentId optional apartment UUID filter.
     * @return ticket report with summary and breakdown list.
     */
    TicketReportResponse getTicketReport(
            LocalDate from,
            LocalDate to,
            String groupBy,
            String category,
            UUID apartmentId
    );

    /**
     * Returns per-amenity booking statistics for the given period.
     *
     * @param from      optional start date; {@code null} defaults to 30 days ago.
     * @param to        optional end date; {@code null} defaults to today.
     * @param amenityId optional amenity filter; {@code null} returns all amenities.
     * @return amenity usage report.
     */
    AmenityUsageReportResponse getAmenityUsageReport(
            LocalDate from,
            LocalDate to,
            UUID amenityId
    );

    /**
     * Returns ACTIVE contracts expiring within the given number of days.
     *
     * @param withinDays look-ahead window in calendar days; defaults to 90.
     * @return contracts-expiring response sorted by earliest expiry first.
     */
    ContractsExpiringResponse getContractsExpiring(int withinDays);

    /**
     * Returns occupancy and demographic totals, optionally scoped to one block.
     *
     * @param blockId optional block UUID; {@code null} returns system-wide totals.
     * @return resident report response.
     */
    ResidentReportResponse getResidentReport(UUID blockId);
}
