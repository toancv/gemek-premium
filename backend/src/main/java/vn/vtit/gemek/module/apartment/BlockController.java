/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.module.apartment.dto.BlockResponse;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateBlockRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for building block management endpoints.
 *
 * <p>List endpoint is accessible to ADMIN, BOARD_MEMBER, and TECHNICIAN.
 * All mutating endpoints require the ADMIN role.
 */
@RestController
@RequestMapping("/api/blocks")
@Tag(name = "Blocks", description = "Building block management")
public class BlockController {

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
     * Returns all blocks sorted by name. No pagination — block count is small and bounded.
     *
     * @return 200 OK with a data-wrapped list of block DTOs.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER','TECHNICIAN')")
    @Operation(summary = "List all blocks")
    public ResponseEntity<Map<String, List<BlockResponse>>> listBlocks() {
        List<BlockResponse> blocks = blockService.listBlocks();
        return ResponseEntity.ok(Map.of("data", blocks));
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
