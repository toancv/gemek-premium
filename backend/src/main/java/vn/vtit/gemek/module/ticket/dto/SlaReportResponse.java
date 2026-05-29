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

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for the SLA report endpoint.
 *
 * <p>Contains a date period, aggregate summary statistics, and a per-category breakdown.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaReportResponse {

    /** The date range covered by this report. */
    private PeriodRef period;

    /** Aggregate statistics across all categories in the period. */
    private SummaryStats summary;

    /** Per-category breakdown of the same statistics. */
    private List<CategoryStats> byCategory;

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Date range covered by the report.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodRef {
        /** Inclusive start date; {@code null} if no lower bound was specified. */
        private LocalDate from;
        /** Inclusive end date; {@code null} if no upper bound was specified. */
        private LocalDate to;
    }

    /**
     * Aggregate SLA statistics across all categories.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        /** Total number of tickets in the period. */
        private long total;
        /** Number of tickets that reached DONE status. */
        private long completed;
        /** Number of tickets that breached their SLA deadline and are still open. */
        private long slaBreached;
        /** {@code slaBreached / total} as a value between 0 and 1. Zero when total is zero. */
        private double slaBreachRate;
        /** Average hours from creation to completion for DONE tickets; {@code null} if no DONE tickets. */
        private Double avgResolutionHours;
        /** Average resident rating across all rated tickets; {@code null} if no ratings. */
        private Double avgRating;
    }

    /**
     * SLA statistics for a single ticket category.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStats {
        /** The ticket category this row covers. */
        private TicketCategory category;
        /** Total number of tickets in this category. */
        private long total;
        /** Number of DONE tickets in this category. */
        private long completed;
        /** Number of SLA-breached open tickets in this category. */
        private long slaBreached;
        /** {@code slaBreached / total} for this category. Zero when total is zero. */
        private double slaBreachRate;
        /** Average resolution hours for DONE tickets in this category; {@code null} if none. */
        private Double avgResolutionHours;
        /** Average resident rating in this category; {@code null} if no ratings. */
        private Double avgRating;
    }
}
