/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.announcement.entity.Announcement;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Announcement} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic filter queries
 * for the admin list endpoint. The named query {@link #findPublishedForApartment}
 * is used exclusively for the resident-scoped list.
 */
@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID>,
        JpaSpecificationExecutor<Announcement> {

    /**
     * Returns a page of published announcements visible to a resident in the given
     * block and floor.
     *
     * <p>An announcement is visible if:
     * <ul>
     *   <li>its scope is {@code ALL}, or</li>
     *   <li>its scope is {@code BLOCK} and its {@code targetBlock} matches {@code blockId}, or</li>
     *   <li>its scope is {@code FLOOR} and both {@code targetBlock} and {@code targetFloor} match.</li>
     * </ul>
     *
     * <p>Only announcements with a non-null {@code publishedAt} are included.
     *
     * @param blockId  the resident's block UUID.
     * @param floor    the resident's floor number.
     * @param pageable pagination and sort parameters.
     * @return a page of matching published announcements.
     */
    @Query("SELECT a FROM Announcement a WHERE a.publishedAt IS NOT NULL AND (" +
           "a.scope = 'ALL' OR " +
           "(a.scope = 'BLOCK' AND a.targetBlock.id = :blockId) OR " +
           "(a.scope = 'FLOOR' AND a.targetBlock.id = :blockId AND a.targetFloor = :floor))")
    Page<Announcement> findPublishedForApartment(
            @Param("blockId") UUID blockId,
            @Param("floor") short floor,
            Pageable pageable);
}
