/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.amenity.entity.Amenity;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Amenity} entity.
 *
 * <p>Provides CRUD operations plus name-uniqueness check queries used by
 * the create and update service methods to enforce {@code uq_amenities_name}.
 * Extends {@link JpaSpecificationExecutor} to support the optional active-status
 * filter on the list endpoint.
 */
@Repository
public interface AmenityRepository extends JpaRepository<Amenity, UUID>, JpaSpecificationExecutor<Amenity> {

    /**
     * Returns whether an amenity with the given name already exists.
     *
     * <p>Used during creation to enforce the unique-name constraint before the INSERT attempt.
     *
     * @param name the amenity name to check.
     * @return {@code true} if any amenity has this name.
     */
    boolean existsByName(String name);

    /**
     * Returns whether an amenity with the given name exists, excluding a specific record by ID.
     *
     * <p>Used during update to allow the same amenity to keep its own name while
     * still preventing collision with other records.
     *
     * @param name      the amenity name to check.
     * @param excludeId the UUID of the amenity being updated (excluded from the check).
     * @return {@code true} if another amenity (not {@code excludeId}) has this name.
     */
    boolean existsByNameAndIdNot(String name, UUID excludeId);
}
