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
}
