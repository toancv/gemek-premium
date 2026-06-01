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
     * @param blockId  optional block filter; {@code null} matches all blocks.
     * @param floor    optional floor filter; {@code null} matches all floors.
     * @param status   optional status filter; {@code null} matches all statuses.
     * @param search   optional case-insensitive substring match on unit_number; {@code null} disables.
     * @param pageable pagination and sort parameters.
     * @return a page of matching apartments with their blocks eagerly loaded.
     */
    @Query("""
            SELECT a FROM Apartment a JOIN FETCH a.block b
            WHERE b.id = COALESCE(:blockId, b.id)
              AND a.floor = COALESCE(:floor, a.floor)
              AND a.status = COALESCE(:status, a.status)
              AND LOWER(a.unitNumber) LIKE LOWER(CONCAT('%', COALESCE(:search, a.unitNumber), '%'))
            """)
    Page<Apartment> findAllWithFilters(
            @Param("blockId") UUID blockId,
            @Param("floor") Short floor,
            @Param("status") ApartmentStatus status,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Returns counts of apartments per status for the dashboard KPI.
     *
     * <p>Each row is {@code [statusText, count]}. All statuses with at least one
     * apartment are represented; missing statuses can be defaulted to 0 by the caller.
     *
     * @return list of two-element rows.
     */
    @Query(value = """
            SELECT a.status::text AS status, COUNT(*) AS cnt
            FROM apartments a
            GROUP BY a.status
            """, nativeQuery = true)
    java.util.List<Object[]> countByStatus();

    /**
     * Counts total apartments optionally filtered by block.
     *
     * <p>Used by the resident report when scoped to a single block.
     *
     * @param blockId optional block UUID; {@code null} means count all apartments.
     * @return total apartment count matching the filter.
     */
    @Query("SELECT COUNT(a) FROM Apartment a WHERE (:blockId IS NULL OR a.block.id = :blockId)")
    long countByOptionalBlock(@Param("blockId") UUID blockId);
}
