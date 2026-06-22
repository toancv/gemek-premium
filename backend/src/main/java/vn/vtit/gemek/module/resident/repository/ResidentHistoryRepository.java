/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.resident.entity.ResidentHistory;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link ResidentHistory} entity.
 *
 * <p>Provides paginated access to the audit log by user or apartment.
 */
@Repository
public interface ResidentHistoryRepository extends JpaRepository<ResidentHistory, UUID> {

    /**
     * Returns a page of history entries for the given user, ordered by event date descending.
     *
     * @param userId   the user UUID to filter by.
     * @param pageable pagination and sorting parameters.
     * @return a page of resident history entries.
     */
    Page<ResidentHistory> findByUserIdOrderByEventDateDesc(UUID userId, Pageable pageable);

    /**
     * Returns a page of history entries for the given apartment, ordered by event date descending.
     *
     * @param apartmentId the apartment UUID to filter by.
     * @param pageable    pagination and sorting parameters.
     * @return a page of resident history entries.
     */
    Page<ResidentHistory> findByApartmentIdOrderByEventDateDesc(UUID apartmentId, Pageable pageable);
}
