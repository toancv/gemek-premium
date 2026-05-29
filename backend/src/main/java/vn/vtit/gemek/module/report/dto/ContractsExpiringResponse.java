/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for {@code GET /api/reports/contracts-expiring}.
 *
 * <p>Lists all ACTIVE contracts whose {@code end_date} falls within the requested
 * {@code withinDays} window, sorted by earliest expiry first.
 */
public record ContractsExpiringResponse(
        LocalDate asOf,
        List<ContractRow> contracts
) {

    /**
     * Summary of a single expiring contract.
     *
     * @param id             contract UUID.
     * @param title          contract title.
     * @param contractor     minimal contractor reference.
     * @param endDate        contract end date.
     * @param daysToExpiry   calendar days from {@code asOf} to {@code endDate} (inclusive).
     * @param contractValue  total monetary value.
     * @param currency       ISO 4217 currency code.
     * @param status         current contract status string.
     */
    public record ContractRow(
            UUID id,
            String title,
            ContractorRef contractor,
            LocalDate endDate,
            long daysToExpiry,
            BigDecimal contractValue,
            String currency,
            String status
    ) {}

    /**
     * Minimal contractor reference used inside {@link ContractRow}.
     *
     * @param id          contractor UUID.
     * @param companyName legal company name.
     */
    public record ContractorRef(UUID id, String companyName) {}
}
