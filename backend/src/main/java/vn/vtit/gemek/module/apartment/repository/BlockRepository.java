/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.apartment.entity.Block;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Block} entity.
 *
 * <p>Provides standard CRUD operations plus name-based lookup methods for
 * uniqueness enforcement and the block list endpoint.
 */
@Repository
public interface BlockRepository extends JpaRepository<Block, UUID> {

    /**
     * Returns whether a block with the given name already exists.
     *
     * @param name the block name to check.
     * @return {@code true} if a block with that name exists.
     */
    boolean existsByName(String name);

    /**
     * Returns whether a block with the given name exists, excluding a specific block ID.
     *
     * <p>Used during update to detect name conflicts with other blocks.
     *
     * @param name      the block name to check.
     * @param excludeId the UUID of the block being updated (excluded from the check).
     * @return {@code true} if another block already uses that name.
     */
    boolean existsByNameAndIdNot(String name, UUID excludeId);

    /**
     * Returns all blocks sorted alphabetically by name.
     *
     * @return list of all blocks ordered by name ascending.
     */
    List<Block> findAllByOrderByNameAsc();
}
