/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Vehicle} entity.
 *
 * <p>Stub created in Module 2 to support apartment detail queries.
 * Additional query methods will be added in Module 3 (Residents and Vehicles).
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    /**
     * Returns all vehicles registered to the given apartment.
     *
     * @param apartmentId the apartment UUID to query.
     * @return list of vehicles associated with that apartment.
     */
    @Query("""
            SELECT v FROM Vehicle v
            WHERE v.apartment.id = :apartmentId
            """)
    List<Vehicle> findByApartmentId(@Param("apartmentId") UUID apartmentId);
}
