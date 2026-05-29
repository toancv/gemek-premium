/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for {@code GET /api/reports/tickets}.
 *
 * <p>Returns aggregated ticket statistics for a given period, with a breakdown
 * list whose granularity is determined by the {@code groupBy} query parameter.
 */
public record TicketReportResponse(
        Period period,
        Summary summary,
        List<BreakdownRow> breakdown
) {

    /**
     * Inclusive date range of the report.
     *
     * @param from start date (inclusive).
     * @param to   end date (inclusive).
     */
    public record Period(LocalDate from, LocalDate to) {}

    /**
     * Aggregate totals across the entire period.
     *
     * @param total          total tickets matching filters.
     * @param completed      tickets with status DONE.
     * @param cancelled      tickets with status CANCELLED.
     * @param inProgress     tickets with status IN_PROGRESS.
     * @param newCount       tickets with status NEW.
     * @param slaBreachRate  proportion of tickets that breached SLA; 0.0 when total is 0.
     * @param avgRating      average resident rating on completed tickets; 0.0 when none rated.
     */
    public record Summary(
            long total,
            long completed,
            long cancelled,
            long inProgress,
            long newCount,
            double slaBreachRate,
            double avgRating
    ) {}

    /**
     * One row in the breakdown list.
     *
     * <p>The {@code label} value depends on {@code groupBy}:
     * <ul>
     *   <li>{@code month}    — {@code "YYYY-MM"}</li>
     *   <li>{@code category} — category enum name</li>
     *   <li>{@code status}   — status enum name</li>
     *   <li>{@code assignee} — assignee display name or {@code "Unassigned"}</li>
     * </ul>
     *
     * @param label       dimension label.
     * @param total       total tickets in this group.
     * @param completed   completed tickets in this group.
     * @param slaBreached tickets that breached SLA in this group.
     * @param avgRating   average rating in this group; 0.0 when none rated.
     */
    public record BreakdownRow(
            String label,
            long total,
            long completed,
            long slaBreached,
            double avgRating
    ) {}
}
