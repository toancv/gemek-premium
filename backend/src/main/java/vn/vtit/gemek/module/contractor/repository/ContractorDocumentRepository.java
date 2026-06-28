/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.contractor.entity.ContractorDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ContractorDocument}.
 *
 * <p>All lookups are scoped by {@code contractorId} so an admin cannot address a document row outside
 * the contractor in the path (dual-key access, mirroring {@code AnnouncementAttachmentRepository}).
 */
@Repository
public interface ContractorDocumentRepository extends JpaRepository<ContractorDocument, UUID> {

    /**
     * Lists all document rows of a contractor, oldest first.
     *
     * @param contractorId the owning contractor id.
     * @return the contractor's document rows.
     */
    List<ContractorDocument> findByContractorIdOrderByCreatedAtAsc(UUID contractorId);

    /**
     * Counts the document rows of a contractor (for the ≤5-documents cap).
     *
     * @param contractorId the owning contractor id.
     * @return the current document count.
     */
    long countByContractorId(UUID contractorId);

    /**
     * Finds a document row by its id AND owning contractor id (dual-key — prevents cross-contractor delete).
     *
     * @param contractorId the owning contractor id.
     * @param id           the document row id.
     * @return the matching document row, or empty.
     */
    Optional<ContractorDocument> findByContractorIdAndId(UUID contractorId, UUID id);

    /**
     * Sums the byte sizes of a contractor's documents (for the ≤50MB total cap).
     *
     * <p>{@code COALESCE} returns 0 when the contractor has no documents (or all sizes null) so the
     * caller never has to null-check the aggregate.
     *
     * @param contractorId the owning contractor id.
     * @return the total stored bytes, 0 when none.
     */
    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM ContractorDocument d "
            + "WHERE d.contractor.id = :contractorId")
    long sumSizeBytesByContractorId(@Param("contractorId") UUID contractorId);
}
