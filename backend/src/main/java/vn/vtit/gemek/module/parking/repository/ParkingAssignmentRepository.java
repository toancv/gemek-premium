/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.parking.entity.ParkingAssignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link ParkingAssignment} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic filter queries
 * on the paginated list endpoint (slot, vehicle, apartment, active filters).
 */
@Repository
public interface ParkingAssignmentRepository extends JpaRepository<ParkingAssignment, UUID>,
        JpaSpecificationExecutor<ParkingAssignment> {

    /**
     * Returns the single active assignment for the given slot, if one exists.
     *
     * <p>An assignment is active when its {@code endDate} is {@code null}.
     *
     * @param slotId the parking slot UUID to query.
     * @return an {@link Optional} containing the active assignment, or empty if none.
     */
    @Query("SELECT a FROM ParkingAssignment a WHERE a.parkingSlot.id = :slotId AND a.endDate IS NULL")
    Optional<ParkingAssignment> findActiveBySlotId(@Param("slotId") UUID slotId);

    /**
     * Returns all active assignments for the given apartment.
     *
     * <p>An assignment is active when its {@code endDate} is {@code null}.
     * An apartment may have multiple active assignments (one per parking slot).
     *
     * @param apartmentId the apartment UUID to query.
     * @return list of active assignments for that apartment.
     */
    @Query("SELECT a FROM ParkingAssignment a WHERE a.apartment.id = :apartmentId AND a.endDate IS NULL")
    List<ParkingAssignment> findActiveByApartmentId(@Param("apartmentId") UUID apartmentId);

    /**
     * Returns whether any assignment (active or historical) exists for the given slot.
     *
     * <p>Used during slot deletion to enforce referential integrity — a slot with any
     * assignment history cannot be deleted.
     *
     * @param slotId the parking slot UUID to check.
     * @return {@code true} if at least one assignment record references this slot.
     */
    boolean existsByParkingSlotId(UUID slotId);
}
