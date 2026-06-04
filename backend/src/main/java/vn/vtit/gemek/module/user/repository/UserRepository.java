/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>Provides standard CRUD operations plus custom query methods for
 * email lookup and the admin user-list endpoint with filters.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by their unique email address.
     *
     * @param email the email address to search for.
     * @return an {@link Optional} containing the user if found.
     */
    Optional<User> findByEmail(String email);

    /**
     * Returns whether a user with the given email already exists.
     *
     * @param email the email address to check.
     * @return {@code true} if a user with that email exists.
     */
    boolean existsByEmail(String email);

    /**
     * Returns whether any user with the given role exists.
     *
     * @param role the role to check.
     * @return {@code true} if at least one user with that role exists.
     */
    boolean existsByRole(UserRole role);

    /**
     * Lists users with optional filters for the admin user-management page.
     *
     * <p>All filter parameters are nullable — passing {@code null} disables that filter.
     *
     * @param role     optional role filter; {@code null} matches all roles.
     * @param isActive optional active-status filter; {@code null} matches both.
     * @param search   optional substring search against full name or email; {@code null} disables.
     * @param pageable pagination and sort parameters.
     * @return a page of matching {@link User} entities.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:isActive IS NULL OR u.active = :isActive)
              AND (:search IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> findAllWithFilters(
            @Param("role") UserRole role,
            @Param("isActive") Boolean isActive,
            @Param("search") String search,
            Pageable pageable);
}
