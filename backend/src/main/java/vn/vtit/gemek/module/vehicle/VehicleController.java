/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle;

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
import vn.vtit.gemek.module.vehicle.dto.CreateVehicleRequest;
import vn.vtit.gemek.module.vehicle.dto.UpdateVehicleRequest;
import vn.vtit.gemek.module.vehicle.dto.VehicleResponse;

import java.util.UUID;

/**
 * REST controller for vehicle registration endpoints.
 *
 * <p>ADMIN has unrestricted access. RESIDENT callers may create, read, update,
 * and soft-delete vehicles scoped to their own active apartment; ownership is
 * enforced by the service layer.
 */
@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Vehicle registration management")
public class VehicleController {

    private final VehicleService vehicleService;

    /**
     * Constructs the controller with the vehicle service dependency.
     *
     * @param vehicleService the vehicle service.
     */
    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /**
     * Returns a paginated list of vehicle records with optional filters.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param search      optional case-insensitive substring matched against licensePlate, brand, and model.
     * @param page        0-based page index (default 0).
     * @param size        page size (default 20, max 100).
     * @return paginated vehicle response DTOs.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List vehicles with optional apartment and search filters")
    public ResponseEntity<PageResponse<VehicleResponse>> listVehicles(
            @RequestParam(required = false) UUID apartmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.asc("apartment.unitNumber"), Sort.Order.asc("licensePlate")));
        return ResponseEntity.ok(vehicleService.listVehicles(apartmentId, search, pageable));
    }

    /**
     * Registers a new vehicle.
     *
     * <p>RESIDENT callers may only register vehicles for their own active apartment.
     *
     * @param req       the create request body.
     * @param principal the authenticated user principal.
     * @return 201 Created with the new vehicle response DTO.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Register a vehicle")
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody CreateVehicleRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.createVehicle(req, principal.getId(), role));
    }

    /**
     * Returns a single vehicle record.
     *
     * <p>RESIDENT callers are restricted to vehicles in their own active apartment.
     *
     * @param id        the vehicle UUID path variable.
     * @param principal the authenticated user principal.
     * @return 200 OK with the vehicle response DTO.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Get a vehicle record")
    public ResponseEntity<VehicleResponse> getVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.ok(vehicleService.getVehicle(id, principal.getId(), role));
    }

    /**
     * Updates mutable fields on a vehicle record.
     *
     * <p>RESIDENT callers are restricted to vehicles in their own active apartment.
     *
     * @param id        the vehicle UUID path variable.
     * @param req       the update request body.
     * @param principal the authenticated user principal.
     * @return 200 OK with the updated vehicle response DTO.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Update a vehicle record")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.ok(vehicleService.updateVehicle(id, req, principal.getId(), role));
    }

    /**
     * Soft-deletes a vehicle by setting its active flag to {@code false}.
     *
     * <p>RESIDENT callers are restricted to vehicles in their own active apartment.
     *
     * @param id        the vehicle UUID path variable.
     * @param principal the authenticated user principal.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Soft-delete a vehicle registration")
    public ResponseEntity<Void> deleteVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        vehicleService.deleteVehicle(id, principal.getId(), role);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the bare role string (without the {@code ROLE_} prefix) from the principal.
     *
     * @param principal the authenticated user principal.
     * @return the role string, e.g. "ADMIN" or "RESIDENT".
     */
    private String extractRole(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
    }
}
