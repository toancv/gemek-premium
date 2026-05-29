/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.ticket.entity.TicketPhoto;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link TicketPhoto} entity.
 */
@Repository
public interface TicketPhotoRepository extends JpaRepository<TicketPhoto, UUID> {

    /**
     * Returns all photos for the given ticket, in their natural insertion order.
     *
     * @param ticketId the ticket UUID to filter on.
     * @return list of photos belonging to the ticket.
     */
    List<TicketPhoto> findByTicketId(UUID ticketId);

    /**
     * Deletes a single photo by ticket and photo UUID.
     *
     * <p>The dual-key check prevents one ticket's admin from deleting photos of another ticket.
     *
     * @param ticketId the parent ticket UUID.
     * @param id       the photo UUID.
     */
    void deleteByTicketIdAndId(UUID ticketId, UUID id);
}
