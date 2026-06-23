/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.announcement.entity.Announcement;

import java.time.OffsetDateTime;
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

    /**
     * Tests whether a resident may READ a given announcement per its ALL/BLOCK/FLOOR scope —
     * the access predicate backing {@code AnnouncementService.assertMediaPresignAccess} (C2.1).
     *
     * <p>True iff the announcement exists, is PUBLISHED ({@code publishedAt} non-null), and at least
     * one of the caller's ACTIVE residencies ({@code moveOutDate IS NULL}) matches the scope. The
     * scope clause is the textual mirror of {@link #findPublishedForApartment} and of
     * {@code ResidentRepository.findRecipientUserIds} — feed↔dispatch↔media visibility stay one rule
     * (guarded by {@code AnnouncementRecipientConsistencyTest}). A nonexistent id, a draft, or an
     * out-of-scope caller all yield false (deny), null-safe — no JPQL nullable-param anchoring needed
     * because {@code a.scope} is a real entity attribute the literal compares against.
     *
     * @param announcementId the announcement UUID parsed from the media object key.
     * @param userId         the calling resident's user UUID.
     * @return true iff the resident may read (and thus presign media for) the announcement.
     */
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Announcement a
            WHERE a.id = :announcementId
              AND a.publishedAt IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM Resident r
                WHERE r.user.id = :userId
                  AND r.moveOutDate IS NULL
                  AND (a.scope = 'ALL'
                    OR (a.scope = 'BLOCK' AND a.targetBlock.id = r.apartment.block.id)
                    OR (a.scope = 'FLOOR' AND a.targetBlock.id = r.apartment.block.id
                          AND a.targetFloor = r.apartment.floor)))
            """)
    boolean existsReadableByResident(@Param("announcementId") UUID announcementId,
                                     @Param("userId") UUID userId);

    /**
     * Atomically publishes a draft announcement (compare-and-set on {@code publishedAt}).
     *
     * <p>Returns 1 only for the single request that transitions the row from draft to
     * published; any concurrent or repeated call sees 0 because the row no longer matches
     * {@code publishedAt IS NULL}. This row-count is the sole already-published / race
     * guard for the publish endpoint — callers map 0 to 409 CONFLICT.
     *
     * @param id  the announcement UUID to publish.
     * @param now the publish timestamp to set.
     * @return number of rows updated — 1 if this call won the publish, 0 otherwise.
     */
    @Modifying
    @Query("UPDATE Announcement a SET a.publishedAt = :now WHERE a.id = :id AND a.publishedAt IS NULL")
    int publishIfDraft(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
