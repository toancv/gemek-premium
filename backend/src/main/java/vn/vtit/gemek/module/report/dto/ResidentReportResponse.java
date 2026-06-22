/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report.dto;

/**
 * Response DTO for {@code GET /api/reports/residents}.
 *
 * <p>Provides occupancy and demographic totals, optionally scoped to a single block.
 *
 * @param totalApartments              total apartments in scope (all or filtered by block).
 * @param occupiedApartments           apartments that have at least one active resident.
 * @param occupancyRate                occupiedApartments / totalApartments; 0.0 when total is 0.
 * @param totalActiveResidents         count of resident records with no move-out date.
 * @param owners                       active residents with type OWNER.
 * @param tenants                      active residents with type TENANT.
 * @param averageResidentsPerApartment totalActiveResidents / occupiedApartments; 0.0 when none occupied.
 */
public record ResidentReportResponse(
        long totalApartments,
        long occupiedApartments,
        double occupancyRate,
        long totalActiveResidents,
        long owners,
        long tenants,
        double averageResidentsPerApartment
) {}
