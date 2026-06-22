/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Vehicle} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic filter queries
 * on the paginated list endpoint.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    /**
     * Returns all vehicles (active and inactive) registered to the given apartment.
     *
     * @param apartmentId the apartment UUID to query.
     * @return list of vehicles associated with that apartment.
     */
    @Query("""
            SELECT v FROM Vehicle v
            WHERE v.apartment.id = :apartmentId
            """)
    List<Vehicle> findByApartmentId(@Param("apartmentId") UUID apartmentId);

    /**
     * Returns whether a vehicle with the given license plate exists, excluding a specific record.
     *
     * <p>Used during update operations to detect plate conflicts while excluding the
     * vehicle being updated.
     *
     * @param licensePlate the plate to check.
     * @param excludeId    the vehicle UUID to exclude from the check.
     * @return {@code true} if another vehicle holds the same plate.
     */
    boolean existsByLicensePlateAndIdNot(String licensePlate, UUID excludeId);

    /**
     * Returns whether any vehicle already has the given license plate.
     *
     * <p>Used during creation to prevent duplicate plate registrations.
     *
     * @param licensePlate the plate to check.
     * @return {@code true} if the plate is already in use.
     */
    boolean existsByLicensePlate(String licensePlate);
}
