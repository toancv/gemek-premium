/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.contractor.entity.Contractor;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Contractor} entity.
 *
 * <p>Stub for Module 4. Full query support is added in Module 5.
 */
@Repository
public interface ContractorRepository extends JpaRepository<Contractor, UUID> {

    /**
     * Recalculates the contractor's average rating from all rated DONE tickets assigned to them.
     *
     * <p>Called by the ticket service after a resident submits a rating on a completed ticket.
     *
     * @param id the contractor UUID whose rating should be recalculated.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE Contractor c
            SET c.rating = (
                SELECT AVG(t.rating)
                FROM Ticket t
                WHERE t.assignedToContractor.id = :id
                  AND t.rating IS NOT NULL
            )
            WHERE c.id = :id
            """)
    void recalculateRating(@Param("id") UUID id);
}
