/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.resident.entity.Resident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Resident} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic filter queries
 * used by the paginated list endpoint.
 */
@Repository
public interface ResidentRepository extends JpaRepository<Resident, UUID>, JpaSpecificationExecutor<Resident> {

    /**
     * Returns all active residents (no move-out date) for the given apartment,
     * fetching the associated user eagerly to avoid N+1 queries.
     *
     * @param apartmentId the apartment UUID to query.
     * @return list of active residents with their user data loaded.
     */
    @Query("""
            SELECT r FROM Resident r
            JOIN FETCH r.user
            WHERE r.apartment.id = :apartmentId
              AND r.moveOutDate IS NULL
            """)
    List<Resident> findActiveByApartmentId(@Param("apartmentId") UUID apartmentId);

    /**
     * Returns the single active resident record for the given user, if any.
     *
     * @param userId the user UUID to query.
     * @return an {@link Optional} containing the active resident, or empty.
     */
    @Query("""
            SELECT r FROM Resident r
            WHERE r.user.id = :userId
              AND r.moveOutDate IS NULL
            """)
    Optional<Resident> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Returns whether the given user has any active resident assignment in any apartment.
     *
     * @param userId the user UUID to check.
     * @return {@code true} if the user is currently an active resident.
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Resident r
            WHERE r.user.id = :userId
              AND r.moveOutDate IS NULL
            """)
    boolean existsActiveByUserId(@Param("userId") UUID userId);

    /**
     * Returns whether the given apartment has any active residents.
     *
     * <p>Used by the Apartment module to prevent deletion of occupied apartments.
     *
     * @param apartmentId the apartment UUID to check.
     * @return {@code true} if at least one active resident exists for this apartment.
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Resident r
            WHERE r.apartment.id = :apartmentId
              AND r.moveOutDate IS NULL
            """)
    boolean existsActiveByApartmentId(@Param("apartmentId") UUID apartmentId);

    /**
     * Returns the active resident record for a specific user in a specific apartment.
     *
     * @param userId      the user UUID to query.
     * @param apartmentId the apartment UUID to query.
     * @return an {@link Optional} containing the matching active resident, or empty.
     */
    @Query("""
            SELECT r FROM Resident r
            WHERE r.user.id = :userId
              AND r.apartment.id = :apartmentId
              AND r.moveOutDate IS NULL
            """)
    Optional<Resident> findActiveByUserIdAndApartmentId(
            @Param("userId") UUID userId,
            @Param("apartmentId") UUID apartmentId);

    /**
     * Returns resident demographics for the report endpoint.
     *
     * <p>Returns one {@code Object[]} row:
     * [totalActive, owners, tenants, occupiedApartments].
     * Optionally filtered by block.
     *
     * @param blockId optional block UUID; {@code null} means all apartments.
     * @return single-element list with one aggregate row.
     */
    @Query(value = """
            SELECT
              COUNT(*)                                                             AS totalActive,
              COUNT(CASE WHEN r.type = 'OWNER'  THEN 1 END)                      AS owners,
              COUNT(CASE WHEN r.type = 'TENANT' THEN 1 END)                      AS tenants,
              COUNT(DISTINCT r.apartment_id)                                      AS occupiedApartments
            FROM residents r
            JOIN apartments a ON a.id = r.apartment_id
            WHERE r.move_out_date IS NULL
              AND (CAST(:blockId AS UUID) IS NULL OR a.block_id = CAST(:blockId AS UUID))
            """, nativeQuery = true)
    List<Object[]> getResidentDemographics(@Param("blockId") UUID blockId);

    /**
     * Resolves the announcement-notification recipient set: distinct user IDs of all
     * ACTIVE residents (no move-out date, active user account) matching the given
     * announcement scope.
     *
     * <p>The WHERE clause is the textual mirror of
     * {@code AnnouncementRepository.findPublishedForApartment} — the single source of
     * the ALL/BLOCK/FLOOR visibility rule. Any change to either predicate must keep the
     * two consistent; this is enforced by the feed↔dispatch consistency contract test
     * ({@code AnnouncementRecipientConsistencyTest}).
     *
     * <p>Staff roles (ADMIN, TECHNICIAN, BOARD_MEMBER) are never resident rows, so they
     * are excluded by construction. Duplicate residencies are collapsed by DISTINCT
     * (additionally prevented by the {@code uq_residents_active_user} partial unique index).
     *
     * @param scope   the announcement scope (ALL, BLOCK, or FLOOR).
     * @param blockId the target block UUID; required for BLOCK and FLOOR scope, null for ALL.
     * @param floor   the target floor; required for FLOOR scope, null otherwise.
     * @return distinct user IDs of recipients.
     */
    default List<UUID> findRecipientUserIds(AnnouncementScope scope, UUID blockId, Short floor) {
        // Delegate with the enum NAME — Hibernate 6 cannot type-anchor an enum parameter
        // that never compares against an entity attribute (no Announcement in this query).
        return findRecipientUserIdsByScopeName(scope.name(), blockId, floor);
    }

    /**
     * String-typed backing query for {@link #findRecipientUserIds}. Do not call directly;
     * use the typed default method.
     *
     * @param scope   the announcement scope name ("ALL", "BLOCK", or "FLOOR").
     * @param blockId the target block UUID; null for ALL scope.
     * @param floor   the target floor; null unless FLOOR scope.
     * @return distinct user IDs of recipients.
     */
    @Query("""
            SELECT DISTINCT u.id FROM Resident r
            JOIN r.user u
            JOIN r.apartment a
            WHERE r.moveOutDate IS NULL
              AND u.active = true
              AND (:scope = 'ALL'
                OR (:scope = 'BLOCK' AND a.block.id = :blockId)
                OR (:scope = 'FLOOR' AND a.block.id = :blockId AND a.floor = :floor))
            """)
    List<UUID> findRecipientUserIdsByScopeName(@Param("scope") String scope,
                                               @Param("blockId") UUID blockId,
                                               @Param("floor") Short floor);
}
