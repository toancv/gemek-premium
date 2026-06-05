/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>Provides standard CRUD operations plus custom query methods for
 * email lookup and the admin user-list endpoint with filters.
 * Extends {@link JpaSpecificationExecutor} so that dynamic optional filters
 * are composed via Criteria API (avoids Hibernate-6 null→bytea type inference
 * issue with JPQL LIKE/LOWER parameters).
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

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
}
