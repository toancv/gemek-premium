/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.resident.entity.Resident;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Resident} entity.
 *
 * <p>Stub created in Module 2 to support apartment detail queries.
 * Additional query methods will be added in Module 3 (Residents and Vehicles).
 */
@Repository
public interface ResidentRepository extends JpaRepository<Resident, UUID> {

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
     * Returns whether the given user has any active resident assignment in any apartment.
     *
     * <p>Used by the User module when checking if a user can be deactivated.
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
     * <p>Used by the Apartment module when checking if an apartment can be deleted.
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
}
