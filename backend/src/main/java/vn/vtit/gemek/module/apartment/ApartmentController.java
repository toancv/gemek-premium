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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.apartment.dto.ApartmentDetailResponse;
import vn.vtit.gemek.module.apartment.dto.ApartmentSummaryResponse;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateApartmentRequest;
import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;

import java.util.UUID;

/**
 * REST controller for apartment management endpoints.
 *
 * <p>List and create/update/delete are restricted to ADMIN and BOARD_MEMBER (list only).
 * The detail endpoint additionally permits RESIDENT access, scoped to the resident's
 * own apartment by the service layer.
 */
@RestController
@RequestMapping("/api/apartments")
@Tag(name = "Apartments", description = "Apartment management")
public class ApartmentController {

    private final ApartmentService apartmentService;

    /**
     * Constructs the controller with the apartment service dependency.
     *
     * @param apartmentService the apartment service.
     */
    public ApartmentController(ApartmentService apartmentService) {
        this.apartmentService = apartmentService;
    }

    /**
     * Returns a paginated list of apartments with optional filters.
     *
     * <p>Default sort: block name asc, floor asc, unitNumber asc.
     *
     * @param blockId   optional block UUID filter.
     * @param floor     optional floor filter.
     * @param status    optional apartment status filter.
     * @param search    optional case-insensitive unit number substring search.
     * @param page      0-based page index (default 0).
     * @param size      page size (default 20, max 100).
     * @return paginated list of apartment summary DTOs.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER')")
    @Operation(summary = "List apartments with filters")
    public ResponseEntity<PageResponse<ApartmentSummaryResponse>> listApartments(
            @RequestParam(required = false) UUID blockId,
            @RequestParam(required = false) Short floor,
            @RequestParam(required = false) ApartmentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Cap page size at 100 to prevent unbounded queries.
        int cappedSize = Math.min(size, 100);

        // Default multi-column sort: block.name asc, floor asc, unitNumber asc.
        Sort sort = Sort.by(Sort.Order.asc("block.name"), Sort.Order.asc("floor"), Sort.Order.asc("unitNumber"));
        Pageable pageable = PageRequest.of(page, cappedSize, sort);

        return ResponseEntity.ok(apartmentService.listApartments(blockId, floor, status, search, pageable));
    }

    /**
     * Creates a new apartment.
     *
     * @param request the create request body.
     * @return 201 Created with the new apartment summary DTO.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an apartment")
    public ResponseEntity<ApartmentSummaryResponse> createApartment(
            @Valid @RequestBody CreateApartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apartmentService.createApartment(request));
    }

    /**
     * Returns full apartment detail including active residents and vehicles.
     *
     * <p>RESIDENT role is permitted but restricted by the service layer to their own apartment.
     *
     * @param id        the apartment UUID path variable.
     * @param principal the authenticated user principal.
     * @return 200 OK with the apartment detail DTO.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER','RESIDENT')")
    @Operation(summary = "Get apartment detail")
    public ResponseEntity<ApartmentDetailResponse> getApartmentDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        // Determine whether the caller is a resident so the service can enforce scoping.
        boolean isResident = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RESIDENT"));

        return ResponseEntity.ok(apartmentService.getApartmentDetail(id, principal.getId(), isResident));
    }

    /**
     * Updates an existing apartment.
     *
     * @param id      the apartment UUID path variable.
     * @param request the update request body.
     * @return 200 OK with the updated apartment summary DTO.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an apartment")
    public ResponseEntity<ApartmentSummaryResponse> updateApartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApartmentRequest request) {
        return ResponseEntity.ok(apartmentService.updateApartment(id, request));
    }

    /**
     * Deletes an apartment. Returns 409 CONFLICT if the apartment has active residents.
     *
     * @param id the apartment UUID path variable.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an apartment")
    public ResponseEntity<Void> deleteApartment(@PathVariable UUID id) {
        apartmentService.deleteApartment(id);
        return ResponseEntity.noContent().build();
    }
}
