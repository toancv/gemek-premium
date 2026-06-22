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
import vn.vtit.gemek.module.parking.entity.ParkingSlot;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link ParkingSlot} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic filter queries
 * on the paginated list endpoint (type, status, zone filters).
 */
@Repository
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, UUID>,
        JpaSpecificationExecutor<ParkingSlot> {

    /**
     * Returns whether a parking slot with the given slot number already exists.
     *
     * <p>Used during creation to prevent duplicate slot numbers.
     *
     * @param slotNumber the slot number to check.
     * @return {@code true} if the slot number is already in use.
     */
    boolean existsBySlotNumber(String slotNumber);

    /**
     * Returns whether another parking slot uses the given slot number, excluding a specific record.
     *
     * <p>Used during update operations to detect conflicts while excluding the slot being updated.
     *
     * @param slotNumber the slot number to check.
     * @param excludeId  the slot UUID to exclude from the check.
     * @return {@code true} if another slot already holds the same number.
     */
    boolean existsBySlotNumberAndIdNot(String slotNumber, UUID excludeId);

    /**
     * Returns whether the given slot has at least one active assignment (end_date IS NULL).
     *
     * <p>Used to enforce the invariant that only one vehicle may be actively assigned
     * to a slot at any given time.
     *
     * @param slotId the parking slot UUID to check.
     * @return {@code true} if an active assignment exists for the slot.
     */
    @Query("SELECT COUNT(a) > 0 FROM ParkingAssignment a WHERE a.parkingSlot.id = :slotId AND a.endDate IS NULL")
    boolean hasActiveAssignment(@Param("slotId") UUID slotId);
}
