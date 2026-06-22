/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Apartment} entity.
 *
 * <p>Provides standard CRUD plus custom queries for the list endpoint filters
 * and conflict detection on unit number uniqueness.
 */
@Repository
public interface ApartmentRepository extends JpaRepository<Apartment, UUID> {

    /**
     * Finds an apartment by UUID, eagerly fetching the associated block.
     *
     * <p>Avoids a second query when building any response that includes the block name.
     *
     * @param id the apartment UUID.
     * @return an {@link Optional} containing the apartment with its block, if found.
     */
    @Query("SELECT a FROM Apartment a JOIN FETCH a.block WHERE a.id = :id")
    Optional<Apartment> findByIdWithBlock(@Param("id") UUID id);

    /**
     * Returns whether a unit number already exists within a block, excluding a specific apartment.
     *
     * <p>Used during update to detect unit number conflicts within the same block.
     *
     * @param blockId    the block UUID to scope the check.
     * @param unitNumber the unit number to check.
     * @param excludeId  the apartment UUID to exclude (the one being updated).
     * @return {@code true} if another apartment in the block uses that unit number.
     */
    boolean existsByBlockIdAndUnitNumberAndIdNot(UUID blockId, String unitNumber, UUID excludeId);

    /**
     * Returns whether any apartment belongs to the given block.
     *
     * <p>Used before deleting a block to enforce the FK constraint at the service layer.
     *
     * @param blockId the block UUID to check.
     * @return {@code true} if at least one apartment references that block.
     */
    boolean existsByBlockId(UUID blockId);

    /**
     * Returns whether a unit number already exists within a given block.
     *
     * <p>Used during create to detect unit number conflicts within the same block.
     *
     * @param blockId    the block UUID to scope the check.
     * @param unitNumber the unit number to check.
     * @return {@code true} if an apartment with that unit number exists in the block.
     */
    boolean existsByBlockIdAndUnitNumber(UUID blockId, String unitNumber);

    /**
     * Paginated apartment list with optional filters.
     *
     * <p>All filter parameters are nullable — passing {@code null} disables that filter.
     * Default sort (block.name asc, floor asc, unitNumber asc) must be supplied via
     * the {@link Pageable} argument by the controller.
     *
     * <p><b>The {@code status} filter matches EFFECTIVE (derived) occupancy, not the stored
     * column.</b> This SQL effective-status logic MUST mirror
     * {@link vn.vtit.gemek.module.apartment.OccupancyResolver} exactly so the filter and the
     * displayed status (list/detail/dashboard all route display through that resolver) cannot
     * disagree:
     * <ul>
     *   <li>{@code MAINTENANCE} → stored status is MAINTENANCE (priority — same as the resolver's
     *       first branch; residents are irrelevant);</li>
     *   <li>{@code OCCUPIED} → not maintenance AND an active resident EXISTS
     *       ({@code move_out_date IS NULL} — the single "occupied" fact, identical to
     *       {@code hasActiveResident} in the resolver);</li>
     *   <li>{@code AVAILABLE} → not maintenance AND NO active resident exists.</li>
     * </ul>
     * This {@code default} method passes the requested status as its {@link ApartmentStatus} NAME
     * (a plain string branch selector — no enum type-anchoring needed) and supplies the
     * {@code MAINTENANCE} constant as a bound enum parameter, which Hibernate anchors against the
     * {@code a.status} attribute and renders with the correct {@code apartment_status} type.
     * (A fully-qualified enum LITERAL in JPQL is rejected by Postgres — it casts to a non-existent
     * {@code apartmentstatus} type.) Spring derives the count query from the SAME JPQL, so the
     * count and the row query apply the IDENTICAL effective-status predicate — totals can never
     * disagree with the rows. Any change here MUST be reflected in {@code OccupancyResolver}
     * (and is locked by the filter↔display agreement test).
     *
     * @param blockId  optional block filter; {@code null} matches all blocks.
     * @param floor    optional floor filter; {@code null} matches all floors.
     * @param status   optional EFFECTIVE status filter; {@code null} matches all.
     * @param search   optional case-insensitive substring match on unit_number; {@code null} disables.
     * @param pageable pagination and sort parameters.
     * @return a page of matching apartments with their blocks eagerly loaded.
     */
    default Page<Apartment> findAllWithFilters(
            UUID blockId, Short floor, ApartmentStatus status, String search, Pageable pageable) {
        // Pass the requested status as its NAME (string branch selector) and MAINTENANCE as a
        // bound enum param so Hibernate types it correctly against a.status.
        return findAllByEffectiveStatus(
                blockId, floor, status == null ? null : status.name(),
                ApartmentStatus.MAINTENANCE, search, pageable);
    }

    /**
     * Backing query for {@link #findAllWithFilters}. Do not call directly — use that default method.
     *
     * <p>{@code statusName} is the requested {@link ApartmentStatus} name (branch selector, nullable);
     * {@code maintenance} is the bound {@code MAINTENANCE} constant, anchored against {@code a.status}.
     *
     * @param blockId     optional block filter; {@code null} matches all blocks.
     * @param floor       optional floor filter; {@code null} matches all floors.
     * @param statusName  requested effective-status name, or {@code null} for no status filter.
     * @param maintenance the {@code MAINTENANCE} constant, bound so Hibernate types it correctly.
     * @param search      optional case-insensitive unit_number substring; {@code null} disables.
     * @param pageable    pagination and sort parameters.
     * @return a page of matching apartments with their blocks eagerly loaded.
     */
    @Query("""
            SELECT a FROM Apartment a JOIN FETCH a.block b
            WHERE b.id = COALESCE(:blockId, b.id)
              AND a.floor = COALESCE(:floor, a.floor)
              AND LOWER(a.unitNumber) LIKE LOWER(CONCAT('%', COALESCE(:search, a.unitNumber), '%'))
              AND (
                :statusName IS NULL
                OR (:statusName = 'MAINTENANCE' AND a.status = :maintenance)
                OR (:statusName = 'OCCUPIED'
                      AND a.status <> :maintenance
                      AND EXISTS (SELECT r FROM Resident r
                                  WHERE r.apartment.id = a.id AND r.moveOutDate IS NULL))
                OR (:statusName = 'AVAILABLE'
                      AND a.status <> :maintenance
                      AND NOT EXISTS (SELECT r FROM Resident r
                                  WHERE r.apartment.id = a.id AND r.moveOutDate IS NULL))
              )
            """)
    Page<Apartment> findAllByEffectiveStatus(
            @Param("blockId") UUID blockId,
            @Param("floor") Short floor,
            @Param("statusName") String statusName,
            @Param("maintenance") ApartmentStatus maintenance,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Returns {@code [id, status]} pairs for all apartments, optionally scoped to a block.
     *
     * <p>Lightweight projection (no block join, no entity hydration) used by the dashboard
     * KPI and resident report to tally effective occupancy via
     * {@link vn.vtit.gemek.module.apartment.OccupancyResolver}.
     * Replaces the former {@code countByStatus} GROUP-BY, which counted the never-synced
     * stored status and therefore reported wrong occupancy.
     *
     * @param blockId optional block UUID; {@code null} means all apartments.
     * @return list of {@code Object[]} rows: {@code [UUID id, ApartmentStatus status]}.
     */
    @Query("SELECT a.id, a.status FROM Apartment a WHERE (:blockId IS NULL OR a.block.id = :blockId)")
    java.util.List<Object[]> findIdAndStatus(@Param("blockId") UUID blockId);
}
