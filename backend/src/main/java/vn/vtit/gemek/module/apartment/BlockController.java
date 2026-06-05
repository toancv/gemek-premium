/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.apartment.dto.BlockResponse;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateBlockRequest;

import java.util.Set;
import java.util.UUID;

/**
 * REST controller for building block management endpoints.
 *
 * <p>The list endpoint supports optional search, pagination, and sort.
 * All mutating endpoints require the ADMIN role.
 */
@RestController
@RequestMapping("/api/blocks")
@Tag(name = "Blocks", description = "Building block management")
public class BlockController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdAt");

    private final BlockService blockService;

    /**
     * Constructs the controller with the block service dependency.
     *
     * @param blockService the block service.
     */
    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    /**
     * Returns a paginated, searchable list of blocks.
     *
     * @param search    optional name substring filter (case-insensitive).
     * @param page      0-based page index (default 0).
     * @param size      page size (default 10, max 200).
     * @param sort      sort field — one of {@code name}, {@code createdAt} (default {@code name}).
     * @param direction sort direction — {@code asc} or {@code desc} (default {@code asc}).
     * @return 200 OK with a {@link PageResponse} of block DTOs.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER','TECHNICIAN')")
    @Operation(summary = "List blocks with optional search, pagination, and sort")
    public ResponseEntity<PageResponse<BlockResponse>> listBlocks(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {

        if (!ALLOWED_SORT_FIELDS.contains(sort)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid sort field: " + sort);
        }
        int cappedSize = Math.min(size, 200);
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(new Sort.Order(sortDir, sort), Sort.Order.asc("id")));

        return ResponseEntity.ok(blockService.listBlocks(search, pageable));
    }

    /**
     * Creates a new block.
     *
     * @param request the create request body.
     * @return 201 Created with the new block DTO.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a block")
    public ResponseEntity<BlockResponse> createBlock(@Valid @RequestBody CreateBlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blockService.createBlock(request));
    }

    /**
     * Updates an existing block.
     *
     * @param id      the block UUID path variable.
     * @param request the update request body.
     * @return 200 OK with the updated block DTO.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a block")
    public ResponseEntity<BlockResponse> updateBlock(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBlockRequest request) {
        return ResponseEntity.ok(blockService.updateBlock(id, request));
    }

    /**
     * Deletes a block. Returns 409 CONFLICT if the block has apartments.
     *
     * @param id the block UUID path variable.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a block")
    public ResponseEntity<Void> deleteBlock(@PathVariable UUID id) {
        blockService.deleteBlock(id);
        return ResponseEntity.noContent().build();
    }
}
