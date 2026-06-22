/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.module.report.dto.AmenityUsageReportResponse;
import vn.vtit.gemek.module.report.dto.ContractsExpiringResponse;
import vn.vtit.gemek.module.report.dto.DashboardResponse;
import vn.vtit.gemek.module.report.dto.ResidentReportResponse;
import vn.vtit.gemek.module.report.dto.TicketReportResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for the Reports and Dashboard module.
 *
 * <p>All endpoints are restricted to {@code ADMIN} or {@code BOARD_MEMBER} roles.
 * No mutations are performed — every method delegates to a read-only service.
 *
 * <ul>
 *   <li>GET /api/reports/dashboard         — dashboard KPIs</li>
 *   <li>GET /api/reports/tickets           — ticket analytics</li>
 *   <li>GET /api/reports/amenity-usage     — amenity booking usage</li>
 *   <li>GET /api/reports/contracts-expiring — expiring contracts</li>
 *   <li>GET /api/reports/residents         — occupancy and demographics</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports & Dashboard", description = "Read-only aggregated reports for ADMIN and BOARD_MEMBER roles")
public class ReportController {

    private final ReportService reportService;

    /**
     * Constructs the controller with the required report service.
     *
     * @param reportService the report service implementation.
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Returns dashboard summary KPIs covering apartments, tickets, amenities, and contracts.
     *
     * @return {@code 200 OK} with a {@link DashboardResponse}.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'BOARD_MEMBER')")
    @Operation(summary = "Dashboard KPIs", description = "Summary metrics for the dashboard landing page")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(reportService.getDashboard());
    }

    /**
     * Returns aggregated ticket statistics for the given period and optional filters.
     *
     * @param from        optional start date (ISO-8601, e.g. {@code 2026-01-01}).
     * @param to          optional end date (ISO-8601); defaults to today.
     * @param groupBy     breakdown dimension — {@code month}, {@code category},
     *                    {@code status}, or {@code assignee}; defaults to {@code month}.
     * @param category    optional ticket category filter.
     * @param apartmentId optional apartment UUID filter.
     * @return {@code 200 OK} with a {@link TicketReportResponse}.
     */
    @GetMapping("/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'BOARD_MEMBER')")
    @Operation(summary = "Ticket analytics report", description = "Aggregated ticket statistics grouped by the requested dimension")
    public ResponseEntity<TicketReportResponse> getTicketReport(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false, defaultValue = "month") String groupBy,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID apartmentId) {
        return ResponseEntity.ok(
                reportService.getTicketReport(from, to, groupBy, category, apartmentId));
    }

    /**
     * Returns per-amenity booking statistics for the given period.
     *
     * @param from      optional start date; defaults to 30 days ago.
     * @param to        optional end date; defaults to today.
     * @param amenityId optional amenity UUID; {@code null} returns all amenities.
     * @return {@code 200 OK} with an {@link AmenityUsageReportResponse}.
     */
    @GetMapping("/amenity-usage")
    @PreAuthorize("hasAnyRole('ADMIN', 'BOARD_MEMBER')")
    @Operation(summary = "Amenity usage report", description = "Booking counts and peak-day per amenity")
    public ResponseEntity<AmenityUsageReportResponse> getAmenityUsageReport(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID amenityId) {
        return ResponseEntity.ok(
                reportService.getAmenityUsageReport(from, to, amenityId));
    }

    /**
     * Returns ACTIVE contracts expiring within the requested number of days.
     *
     * @param withinDays look-ahead window in calendar days; defaults to {@code 90}.
     * @return {@code 200 OK} with a {@link ContractsExpiringResponse}.
     */
    @GetMapping("/contracts-expiring")
    @PreAuthorize("hasAnyRole('ADMIN', 'BOARD_MEMBER')")
    @Operation(summary = "Expiring contracts", description = "ACTIVE contracts whose end date falls within the look-ahead window")
    public ResponseEntity<ContractsExpiringResponse> getContractsExpiring(
            @RequestParam(required = false, defaultValue = "90") int withinDays) {
        return ResponseEntity.ok(reportService.getContractsExpiring(withinDays));
    }

    /**
     * Returns occupancy and resident demographic totals, optionally scoped to one block.
     *
     * @param blockId optional block UUID; {@code null} returns system-wide totals.
     * @return {@code 200 OK} with a {@link ResidentReportResponse}.
     */
    @GetMapping("/residents")
    @PreAuthorize("hasAnyRole('ADMIN', 'BOARD_MEMBER')")
    @Operation(summary = "Resident occupancy report", description = "Occupancy rate and resident demographic breakdown")
    public ResponseEntity<ResidentReportResponse> getResidentReport(
            @RequestParam(required = false) UUID blockId) {
        return ResponseEntity.ok(reportService.getResidentReport(blockId));
    }
}
