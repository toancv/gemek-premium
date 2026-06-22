/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.ticket.entity.TicketStatusHistory;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link TicketStatusHistory} entity.
 *
 * <p>Append-only — no update or delete operations are exposed.
 */
@Repository
public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, UUID> {

    /**
     * Returns the full status history for a ticket in chronological order.
     *
     * @param ticketId the ticket UUID to query.
     * @return list of history entries ordered by {@code changedAt} ascending.
     */
    List<TicketStatusHistory> findByTicketIdOrderByChangedAtAsc(UUID ticketId);
}
