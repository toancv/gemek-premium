/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.ticket.entity.Ticket;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Ticket} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic filter queries
 * used by the paginated list endpoint and role-scoped access control.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    /**
     * Aggregates ticket SLA statistics grouped by category for the report endpoint.
     *
     * <p>Returns one row per category. Each row contains: category string, total count,
     * completed count, SLA-breached count, average resolution hours, and average rating.
     *
     * @param from     optional lower bound on {@code created_at} (inclusive); {@code null} means no lower bound.
     * @param to       optional upper bound on {@code created_at} (inclusive); {@code null} means no upper bound.
     * @param category optional category string filter; {@code null} means all categories.
     * @return list of {@code Object[]} rows, one per category.
     */
    @Query(value = """
            SELECT t.category                                                                   AS category,
                   COUNT(*)                                                                     AS total,
                   COUNT(CASE WHEN t.status = 'DONE' THEN 1 END)                               AS completed,
                   COUNT(CASE WHEN t.sla_deadline < NOW()
                                   AND t.status NOT IN ('DONE','CANCELLED') THEN 1 END)        AS slaBreached,
                   AVG(CASE WHEN t.status = 'DONE'
                            THEN EXTRACT(EPOCH FROM (t.completed_date - t.created_at)) / 3600
                        END)                                                                    AS avgResolutionHours,
                   AVG(t.rating)                                                                AS avgRating
            FROM tickets t
            WHERE (:from IS NULL OR t.created_at >= :from)
              AND (:to   IS NULL OR t.created_at <= :to)
              AND (:category IS NULL OR t.category = CAST(:category AS ticket_category))
            GROUP BY t.category
            """, nativeQuery = true)
    List<Object[]> getSlaReportByCategory(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("category") String category
    );

    /**
     * Aggregates ticket counts and SLA/rating metrics for the report endpoint,
     * grouped by the requested dimension (month, category, status, or assignee).
     *
     * <p>The {@code groupBy} label is computed in the SELECT:
     * <ul>
     *   <li>{@code month}    — {@code TO_CHAR(created_at, 'YYYY-MM')}</li>
     *   <li>{@code category} — category column cast to text</li>
     *   <li>{@code status}   — status column cast to text</li>
     *   <li>{@code assignee} — assigned technician full name, or {@code 'Unassigned'}</li>
     * </ul>
     *
     * @param from      optional lower bound on {@code created_at}; {@code null} means no lower bound.
     * @param to        optional upper bound on {@code created_at}; {@code null} means no upper bound.
     * @param category  optional category string filter; {@code null} means all.
     * @param apartmentId optional apartment UUID filter; {@code null} means all.
     * @param groupBy   dimension string: {@code "month"}, {@code "category"}, {@code "status"}, or {@code "assignee"}.
     * @return list of {@code Object[]} rows: [label, total, completed, slaBreached, avgRating].
     */
    @Query(value = """
            SELECT
              CASE CAST(:groupBy AS TEXT)
                WHEN 'month'    THEN TO_CHAR(t.created_at AT TIME ZONE 'UTC', 'YYYY-MM')
                WHEN 'category' THEN t.category::text
                WHEN 'status'   THEN t.status::text
                WHEN 'assignee' THEN COALESCE(u.full_name, 'Unassigned')
                ELSE t.category::text
              END                                                                            AS label,
              COUNT(*)                                                                       AS total,
              COUNT(CASE WHEN t.status = 'DONE' THEN 1 END)                                AS completed,
              COUNT(CASE WHEN t.sla_deadline < NOW()
                              AND t.status NOT IN ('DONE','CANCELLED') THEN 1 END)          AS slaBreached,
              COALESCE(AVG(t.rating), 0)                                                    AS avgRating
            FROM tickets t
            LEFT JOIN users u ON u.id = t.assigned_to_user_id
            WHERE (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.created_at >= :from)
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR t.created_at <= :to)
              AND (CAST(:category AS TEXT) IS NULL OR t.category = CAST(:category AS ticket_category))
              AND (CAST(:apartmentId AS UUID) IS NULL OR t.apartment_id = :apartmentId)
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> getTicketBreakdown(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("category") String category,
            @Param("apartmentId") UUID apartmentId,
            @Param("groupBy") String groupBy
    );

    /**
     * Returns aggregate ticket summary totals for the report period.
     *
     * <p>Returns a single {@code Object[]} row:
     * [total, completed, cancelled, inProgress, newCount, slaBreached, avgRating].
     *
     * @param from        optional lower bound; {@code null} means no lower bound.
     * @param to          optional upper bound; {@code null} means no upper bound.
     * @param category    optional category filter.
     * @param apartmentId optional apartment UUID filter.
     * @return single-element list with one {@code Object[]} row of aggregates.
     */
    @Query(value = """
            SELECT
              COUNT(*)                                                                       AS total,
              COUNT(CASE WHEN t.status = 'DONE' THEN 1 END)                                AS completed,
              COUNT(CASE WHEN t.status = 'CANCELLED' THEN 1 END)                           AS cancelled,
              COUNT(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 END)                        AS inProgress,
              COUNT(CASE WHEN t.status = 'NEW' THEN 1 END)                                AS newCount,
              COUNT(CASE WHEN t.sla_deadline < NOW()
                              AND t.status NOT IN ('DONE','CANCELLED') THEN 1 END)         AS slaBreached,
              COALESCE(AVG(t.rating), 0)                                                   AS avgRating
            FROM tickets t
            WHERE (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.created_at >= :from)
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR t.created_at <= :to)
              AND (CAST(:category AS TEXT) IS NULL OR t.category = CAST(:category AS ticket_category))
              AND (CAST(:apartmentId AS UUID) IS NULL OR t.apartment_id = :apartmentId)
            """, nativeQuery = true)
    List<Object[]> getTicketSummary(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("category") String category,
            @Param("apartmentId") UUID apartmentId
    );

    /**
     * Returns dashboard-level ticket KPIs in a single query.
     *
     * <p>Returns one {@code Object[]} row:
     * [openRequests, inProgressRequests, overdueRequests, avgResolutionHours30d].
     *
     * @param since30Days 30 days before today, used for avgResolutionHours filter.
     * @return single-element list with one aggregate row.
     */
    @Query(value = """
            SELECT
              COUNT(CASE WHEN t.status = 'NEW' THEN 1 END)                                          AS openRequests,
              COUNT(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 END)                                 AS inProgressRequests,
              COUNT(CASE WHEN t.sla_deadline < NOW()
                              AND t.status NOT IN ('DONE','CANCELLED') THEN 1 END)                  AS overdueRequests,
              COALESCE(AVG(CASE WHEN t.status = 'DONE'
                                     AND t.created_at >= :since30Days
                                THEN EXTRACT(EPOCH FROM (t.completed_date - t.created_at)) / 3600
                           END), 0)                                                                  AS avgResHours
            FROM tickets t
            """, nativeQuery = true)
    List<Object[]> getDashboardTicketKpis(@Param("since30Days") OffsetDateTime since30Days);

    /**
     * Returns active tickets whose SLA deadline has passed and whose breach
     * notification has not been sent yet (N3 P6 overdue scan).
     *
     * <p>The {@code slaOverdueNotifiedAt IS NULL} sent-marker predicate makes the
     * scan idempotent — the scheduler sets the marker in the same transaction as
     * the notification insert. SUGGESTION_FEEDBACK tickets carry a {@code null}
     * deadline and never match.
     *
     * @param now the scan timestamp (single snapshot shared with the warning scan).
     * @return overdue tickets pending their breach notification.
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.status NOT IN ('DONE', 'CANCELLED')
              AND t.slaDeadline IS NOT NULL
              AND t.slaDeadline < :now
              AND t.slaOverdueNotifiedAt IS NULL
            """)
    List<Ticket> findSlaOverdueCandidates(@Param("now") OffsetDateTime now);

    /**
     * Returns active tickets entering the SLA warning window whose warning
     * notification has not been sent yet (N3 P6 warning scan).
     *
     * <p>The lower bound {@code slaDeadline >= :now} excludes already-overdue
     * tickets — those are handled exclusively by the overdue scan, which sets BOTH
     * markers (an "approaching deadline" warning after the breach is pointless).
     *
     * @param now           the scan timestamp (single snapshot shared with the overdue scan).
     * @param warningCutoff {@code now} plus the fixed warning lead time (G2: 2 hours).
     * @return tickets inside the warning window pending their warning notification.
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.status NOT IN ('DONE', 'CANCELLED')
              AND t.slaDeadline IS NOT NULL
              AND t.slaDeadline >= :now
              AND t.slaDeadline < :warningCutoff
              AND t.slaWarningNotifiedAt IS NULL
            """)
    List<Ticket> findSlaWarningCandidates(@Param("now") OffsetDateTime now,
                                          @Param("warningCutoff") OffsetDateTime warningCutoff);

    /**
     * Returns counts of open+in-progress tickets grouped by category for the dashboard.
     *
     * <p>Each row is {@code [categoryText, count]}.
     *
     * @return list of two-element rows.
     */
    @Query(value = """
            SELECT t.category::text AS cat, COUNT(*) AS cnt
            FROM tickets t
            WHERE t.status IN ('NEW','IN_PROGRESS')
            GROUP BY t.category
            """, nativeQuery = true)
    List<Object[]> countActiveByCategory();
}
