/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.apartment.dto.BlockResponse;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateBlockRequest;

import java.util.UUID;

/**
 * Service interface for block management operations.
 *
 * <p>Defines the contract for creating, reading, updating, and deleting blocks.
 * Implementations must enforce uniqueness and deletion constraints.
 */
public interface BlockService {

    /**
     * Returns a paginated, searchable list of blocks sorted by the given pageable.
     *
     * @param search   optional name substring filter (case-insensitive); ignored when null or blank.
     * @param pageable page/size/sort from the request; default size=10, sort=name asc.
     * @return paginated {@link PageResponse} of block DTOs.
     */
    PageResponse<BlockResponse> listBlocks(String search, Pageable pageable);

    /**
     * Creates a new block.
     *
     * @param request the create request containing the block name and optional description.
     * @return the created block response DTO.
     * @throws vn.vtit.gemek.common.exception.AppException with CONFLICT if the name already exists.
     */
    BlockResponse createBlock(CreateBlockRequest request);

    /**
     * Updates an existing block.
     *
     * @param id      the UUID of the block to update.
     * @param request the update request containing the new name and description.
     * @return the updated block response DTO.
     * @throws vn.vtit.gemek.common.exception.AppException with NOT_FOUND if the block does not exist.
     * @throws vn.vtit.gemek.common.exception.AppException with CONFLICT if the new name conflicts with another block.
     */
    BlockResponse updateBlock(UUID id, UpdateBlockRequest request);

    /**
     * Deletes a block by UUID.
     *
     * @param id the UUID of the block to delete.
     * @throws vn.vtit.gemek.common.exception.AppException with NOT_FOUND if the block does not exist.
     * @throws vn.vtit.gemek.common.exception.AppException with CONFLICT if the block has associated apartments.
     */
    void deleteBlock(UUID id);
}
