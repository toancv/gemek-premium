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
import vn.vtit.gemek.module.resident.entity.Resident;

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
     * Returns the single active resident record for the given user, if any.
     *
     * @param userId the user UUID to query.
     * @return an {@link Optional} containing the active resident, or empty.
     */
    @Query("""
            SELECT r FROM Resident r
            WHERE r.user.id = :userId
              AND r.moveOutDate IS NULL
            """)
    Optional<Resident> findActiveByUserId(@Param("userId") UUID userId);

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
}
