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

import java.util.Collection;
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
     * Batch variant of {@link #findActiveByApartmentId}: returns all active residents
     * (no move-out date) for ANY of the given apartments in a single query, with the
     * user fetched eagerly. Used by the apartment list endpoint to resolve occupancy
     * and primary-contact for a whole page without a per-row query (N+1 avoidance).
     *
     * @param apartmentIds the apartment UUIDs to query; must be non-empty.
     * @return active residents across all given apartments, user eagerly loaded.
     */
    @Query("""
            SELECT r FROM Resident r
            JOIN FETCH r.user
            WHERE r.apartment.id IN :apartmentIds
              AND r.moveOutDate IS NULL
            """)
    List<Resident> findActiveByApartmentIdIn(@Param("apartmentIds") Collection<UUID> apartmentIds);

    /**
     * Returns the IDs of all apartments that currently have at least one active resident
     * (a resident with {@code move_out_date IS NULL}), optionally scoped to a block.
     *
     * <p>This is the single SQL expression of the "occupied" fact. The dashboard KPI and
     * the resident report both consume it (combined with
     * {@link vn.vtit.gemek.module.apartment.OccupancyResolver} for the MAINTENANCE-priority rule)
     * so their occupancy numbers cannot diverge.
     *
     * @param blockId optional block UUID; {@code null} means all blocks.
     * @return distinct apartment IDs with an active resident.
     */
    @Query("""
            SELECT DISTINCT r.apartment.id FROM Resident r
            WHERE r.moveOutDate IS NULL
              AND (:blockId IS NULL OR r.apartment.block.id = :blockId)
            """)
    List<UUID> findOccupiedApartmentIds(@Param("blockId") UUID blockId);

    /**
     * Returns the single active resident record for the given user, if any.
     *
     * @deprecated UNSAFE under multi-residency. This is an {@link Optional}-returning query with
     *     <strong>no {@code LIMIT}</strong>: once {@code uq_residents_active_user} is relaxed to
     *     {@code (user_id, apartment_id)} (residency-lifecycle P2), a user with two active rows makes
     *     this throw {@code NonUniqueResultException} at runtime. Do NOT call it. Use
     *     {@link #findAllActiveByUserId(UUID)} for the full set, {@link #findActiveApartmentIdsByUserId(UUID)}
     *     for the active-apartment id set, {@link #existsActiveByUserIdAndApartmentId(UUID, UUID)} for a
     *     per-apartment membership check, or {@link #findActiveByUserIdAndApartmentId(UUID, UUID)} when an
     *     apartment context is known. As of P1 no production code calls this; retained pending a separate
     *     cleanup (not deleted in P1).
     * @param userId the user UUID to query.
     * @return an {@link Optional} containing the active resident, or empty.
     */
    @Deprecated
    @Query("""
            SELECT r FROM Resident r
            WHERE r.user.id = :userId
              AND r.moveOutDate IS NULL
            """)
    Optional<Resident> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Returns ALL active resident records (no move-out date) for the given user, across every apartment,
     * with user, apartment, and block fetched eagerly to avoid N+1 in the mapper.
     *
     * <p>Multi-residency-safe replacement for {@link #findActiveByUserId(UUID)}: returns 0, 1, or 2+ rows
     * without throwing. Ordered by primary-contact first, then latest move-in, then id — so callers that
     * need a single deterministic "default" residency (amenity booking, pending its real attribution rule)
     * can take the first element.
     *
     * @param userId the user UUID to query.
     * @return all active residencies for the user, ordered (primary, then latest move-in, then id).
     */
    @Query("""
            SELECT r FROM Resident r
            JOIN FETCH r.user
            JOIN FETCH r.apartment a
            JOIN FETCH a.block
            WHERE r.user.id = :userId
              AND r.moveOutDate IS NULL
            ORDER BY r.primaryContact DESC, r.moveInDate DESC, r.id DESC
            """)
    List<Resident> findAllActiveByUserId(@Param("userId") UUID userId);

    /**
     * Returns the IDs of all apartments the given user is currently an active resident of, in a single
     * query (N+1-safe). Multi-residency-safe: returns the full set, never throws.
     *
     * <p>Used by ticket "my tickets" scoping/redaction — "my apartments" is the set the caller actively
     * resides in, not a single derived apartment.
     *
     * @param userId the user UUID to query.
     * @return distinct-by-construction list of the user's active apartment ids (empty if none).
     */
    @Query("""
            SELECT r.apartment.id FROM Resident r
            WHERE r.user.id = :userId
              AND r.moveOutDate IS NULL
            """)
    List<UUID> findActiveApartmentIdsByUserId(@Param("userId") UUID userId);

    /**
     * Returns whether the given user has an active residency in the specified apartment.
     *
     * <p>Multi-residency-safe membership check (COUNT&gt;0, no entity load). Replaces the
     * "load the user's one residency then compare its apartment" pattern in per-context ownership and
     * visibility guards: the user may act on a target IFF they actively reside in THAT target's apartment.
     *
     * @param userId      the user UUID to check.
     * @param apartmentId the apartment UUID to check membership of.
     * @return {@code true} if the user has an active residency in that apartment.
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Resident r
            WHERE r.user.id = :userId
              AND r.apartment.id = :apartmentId
              AND r.moveOutDate IS NULL
            """)
    boolean existsActiveByUserIdAndApartmentId(@Param("userId") UUID userId,
                                               @Param("apartmentId") UUID apartmentId);

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
     * <p>Returns one {@code Object[]} row: [totalActive, owners, tenants].
     * Optionally filtered by block. Occupancy (occupied-apartment count) is intentionally
     * NOT returned here — it is derived via
     * {@link vn.vtit.gemek.module.apartment.OccupancyResolver} so it honors MAINTENANCE priority.
     *
     * @param blockId optional block UUID; {@code null} means all apartments.
     * @return single-element list with one aggregate row.
     */
    @Query(value = """
            SELECT
              COUNT(*)                                                             AS totalActive,
              COUNT(CASE WHEN r.type = 'OWNER'  THEN 1 END)                      AS owners,
              COUNT(CASE WHEN r.type = 'TENANT' THEN 1 END)                      AS tenants
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
