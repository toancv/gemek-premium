/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking;

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
import vn.vtit.gemek.module.parking.dto.CheckoutRequest;
import vn.vtit.gemek.module.parking.dto.CreateAssignmentRequest;
import vn.vtit.gemek.module.parking.dto.CreateGuestVehicleRequest;
import vn.vtit.gemek.module.parking.dto.CreateParkingSlotRequest;
import vn.vtit.gemek.module.parking.dto.GuestVehicleResponse;
import vn.vtit.gemek.module.parking.dto.ParkingAssignmentResponse;
import vn.vtit.gemek.module.parking.dto.ParkingSlotResponse;
import vn.vtit.gemek.module.parking.dto.UnassignRequest;
import vn.vtit.gemek.module.parking.dto.UpdateParkingSlotRequest;
import vn.vtit.gemek.module.parking.entity.ParkingSlotStatus;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;

import java.util.UUID;

/**
 * REST controller for all parking management endpoints.
 *
 * <p>Covers three sub-domains under the {@code /api/parking} base path:
 * <ul>
 *   <li>Slot CRUD — {@code /api/parking/slots}</li>
 *   <li>Assignment lifecycle — {@code /api/parking/slots/{id}/assign|unassign}
 *       and {@code /api/parking/assignments}</li>
 *   <li>Guest vehicle log — {@code /api/parking/guests}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/parking")
@Tag(name = "Parking", description = "Parking slot management, assignments, and guest vehicle logging")
public class ParkingController {

    private final ParkingService parkingService;

    /**
     * Constructs the controller with the parking service dependency.
     *
     * @param parkingService the parking service.
     */
    public ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    // =========================================================================
    // Parking slots
    // =========================================================================

    /**
     * Returns a paginated list of parking slots with optional filters.
     *
     * @param type      optional slot type filter.
     * @param status    optional slot status filter.
     * @param zone      optional zone substring filter.
     * @param page      0-based page index (default 0).
     * @param size      page size (default 20, max 100).
     * @param direction sort direction — "asc" or "desc" (default "asc").
     * @return paginated slot response DTOs.
     */
    @GetMapping("/slots")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "List parking slots")
    public ResponseEntity<PageResponse<ParkingSlotResponse>> listSlots(
            @RequestParam(required = false) ParkingSlotType type,
            @RequestParam(required = false) ParkingSlotStatus status,
            @RequestParam(required = false) String zone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "asc") String direction) {

        int cappedSize = Math.min(size, 100);
        Sort sort = "desc".equalsIgnoreCase(direction)
                ? Sort.by(Sort.Order.desc("slotNumber"))
                : Sort.by(Sort.Order.asc("slotNumber"));
        Pageable pageable = PageRequest.of(page, cappedSize, sort);
        return ResponseEntity.ok(parkingService.listSlots(type, status, zone, pageable));
    }

    /**
     * Creates a new parking slot.
     *
     * @param req the creation request body.
     * @return 201 Created with the new slot response DTO.
     */
    @PostMapping("/slots")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a parking slot")
    public ResponseEntity<ParkingSlotResponse> createSlot(
            @Valid @RequestBody CreateParkingSlotRequest req) {

        return ResponseEntity.status(HttpStatus.CREATED).body(parkingService.createSlot(req));
    }

    /**
     * Returns a single parking slot by ID.
     *
     * @param id the slot UUID path variable.
     * @return 200 OK with the slot response DTO.
     */
    @GetMapping("/slots/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Get a parking slot")
    public ResponseEntity<ParkingSlotResponse> getSlot(@PathVariable UUID id) {
        return ResponseEntity.ok(parkingService.getSlot(id));
    }

    /**
     * Updates mutable fields on a parking slot.
     *
     * @param id  the slot UUID path variable.
     * @param req the update request body.
     * @return 200 OK with the updated slot response DTO.
     */
    @PutMapping("/slots/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a parking slot")
    public ResponseEntity<ParkingSlotResponse> updateSlot(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateParkingSlotRequest req) {

        return ResponseEntity.ok(parkingService.updateSlot(id, req));
    }

    /**
     * Deletes a parking slot.
     *
     * <p>Fails with 409 CONFLICT if the slot has any assignment history.
     *
     * @param id the slot UUID path variable.
     * @return 204 No Content.
     */
    @DeleteMapping("/slots/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a parking slot")
    public ResponseEntity<Void> deleteSlot(@PathVariable UUID id) {
        parkingService.deleteSlot(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Assignment lifecycle
    // =========================================================================

    /**
     * Assigns a parking slot to a vehicle, setting slot status to OCCUPIED.
     *
     * @param id        the parking slot UUID path variable.
     * @param req       the assignment creation request body.
     * @param principal the authenticated user principal.
     * @return 201 Created with the new assignment response DTO.
     */
    @PostMapping("/slots/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a parking slot to a vehicle")
    public ResponseEntity<ParkingAssignmentResponse> assignSlot(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAssignmentRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(parkingService.assignSlot(id, req, principal.getId()));
    }

    /**
     * Ends the active assignment for a slot, setting slot status back to AVAILABLE.
     *
     * @param id  the parking slot UUID path variable.
     * @param req the unassign request body carrying the optional end date.
     * @return 200 OK with the updated assignment response DTO.
     */
    @PostMapping("/slots/{id}/unassign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "End the active assignment for a parking slot")
    public ResponseEntity<ParkingAssignmentResponse> unassignSlot(
            @PathVariable UUID id,
            @RequestBody(required = false) UnassignRequest req) {

        // Allow an empty body — treat it as no preferred end date.
        UnassignRequest safeReq = (req != null) ? req : new UnassignRequest();
        return ResponseEntity.ok(parkingService.unassignSlot(id, safeReq));
    }

    /**
     * Returns a paginated list of assignments with optional filters.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param slotId      optional slot UUID filter.
     * @param active      optional active-only filter.
     * @param page        0-based page index (default 0).
     * @param size        page size (default 20, max 100).
     * @return paginated assignment response DTOs.
     */
    @GetMapping("/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List parking assignments")
    public ResponseEntity<PageResponse<ParkingAssignmentResponse>> listAssignments(
            @RequestParam(required = false) UUID apartmentId,
            @RequestParam(required = false) UUID slotId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("startDate")));
        return ResponseEntity.ok(parkingService.listAssignments(apartmentId, slotId, active, pageable));
    }

    /**
     * Returns a single assignment by ID.
     *
     * @param id the assignment UUID path variable.
     * @return 200 OK with the assignment response DTO.
     */
    @GetMapping("/assignments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a parking assignment")
    public ResponseEntity<ParkingAssignmentResponse> getAssignment(@PathVariable UUID id) {
        return ResponseEntity.ok(parkingService.getAssignment(id));
    }

    // =========================================================================
    // Guest vehicles
    // =========================================================================

    /**
     * Returns a paginated list of guest vehicle records with optional filters.
     *
     * @param apartmentId optional host apartment UUID filter.
     * @param active      optional active-only filter ({@code true} = not yet checked out).
     * @param page        0-based page index (default 0).
     * @param size        page size (default 20, max 100).
     * @return paginated guest vehicle response DTOs.
     */
    @GetMapping("/guests")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "List guest vehicle entries")
    public ResponseEntity<PageResponse<GuestVehicleResponse>> listGuestVehicles(
            @RequestParam(required = false) UUID apartmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("entryTime")));
        return ResponseEntity.ok(parkingService.listGuestVehicles(apartmentId, active, pageable));
    }

    /**
     * Logs a guest vehicle entry.
     *
     * @param req       the guest vehicle creation request body.
     * @param principal the authenticated user principal.
     * @return 201 Created with the new guest vehicle response DTO.
     */
    @PostMapping("/guests")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Log a guest vehicle entry")
    public ResponseEntity<GuestVehicleResponse> logGuestVehicle(
            @Valid @RequestBody CreateGuestVehicleRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(parkingService.logGuestVehicle(req, principal.getId()));
    }

    /**
     * Records the exit of a guest vehicle.
     *
     * @param id  the guest vehicle record UUID path variable.
     * @param req the checkout request body carrying the optional exit time.
     * @return 200 OK with the updated guest vehicle response DTO.
     */
    @PutMapping("/guests/{id}/checkout")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Record guest vehicle exit")
    public ResponseEntity<GuestVehicleResponse> checkoutGuest(
            @PathVariable UUID id,
            @RequestBody(required = false) CheckoutRequest req) {

        // Allow an empty body — treat it as no preferred exit time.
        CheckoutRequest safeReq = (req != null) ? req : new CheckoutRequest();
        return ResponseEntity.ok(parkingService.checkoutGuest(id, safeReq));
    }
}
