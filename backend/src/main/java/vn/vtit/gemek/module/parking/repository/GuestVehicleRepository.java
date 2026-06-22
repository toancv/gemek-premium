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
import vn.vtit.gemek.module.parking.entity.GuestVehicle;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link GuestVehicle} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic filter queries
 * on the paginated list endpoint (apartment, date range, license plate filters).
 */
@Repository
public interface GuestVehicleRepository extends JpaRepository<GuestVehicle, UUID>,
        JpaSpecificationExecutor<GuestVehicle> {

    /**
     * Returns all guest vehicle records for the given apartment that have not yet checked out.
     *
     * <p>A guest vehicle is considered still on premises when {@code exitTime} is {@code null}.
     *
     * @param apartmentId the host apartment UUID to query.
     * @return list of active (not yet checked-out) guest vehicle records.
     */
    @Query("SELECT g FROM GuestVehicle g WHERE g.hostApartment.id = :apartmentId AND g.exitTime IS NULL")
    List<GuestVehicle> findActiveByApartmentId(@Param("apartmentId") UUID apartmentId);
}
