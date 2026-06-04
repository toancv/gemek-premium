/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.amenity.dto.AmenityBookingResponse;
import vn.vtit.gemek.module.amenity.dto.AmenityResponse;
import vn.vtit.gemek.module.amenity.dto.ApproveRejectRequest;
import vn.vtit.gemek.module.amenity.dto.AvailabilityResponse;
import vn.vtit.gemek.module.amenity.dto.CreateAmenityRequest;
import vn.vtit.gemek.module.amenity.dto.CreateBookingRequest;
import vn.vtit.gemek.module.amenity.dto.UpdateAmenityRequest;
import vn.vtit.gemek.module.amenity.entity.BookingStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for the Amenity Booking module.
 *
 * <p>Handles two resource paths:
 * <ul>
 *   <li>{@code /api/amenities}         — amenity CRUD and availability calendar.</li>
 *   <li>{@code /api/amenity-bookings}  — booking creation, listing, approval, and cancellation.</li>
 * </ul>
 *
 * <p>Role access summary:
 * <ul>
 *   <li>GET  /api/amenities                       — ADMIN, TECHNICIAN, RESIDENT, BOARD_MEMBER</li>
 *   <li>POST /api/amenities                       — ADMIN</li>
 *   <li>GET  /api/amenities/{id}                  — all authenticated</li>
 *   <li>PUT  /api/amenities/{id}                  — ADMIN</li>
 *   <li>DELETE /api/amenities/{id}                — ADMIN</li>
 *   <li>GET  /api/amenities/{id}/availability     — all authenticated</li>
 *   <li>GET  /api/amenity-bookings                — ADMIN, TECHNICIAN, BOARD_MEMBER (all); RESIDENT (own)</li>
 *   <li>POST /api/amenity-bookings                — ADMIN, RESIDENT</li>
 *   <li>GET  /api/amenity-bookings/{id}           — ADMIN, RESIDENT (own)</li>
 *   <li>PUT  /api/amenity-bookings/{id}/approve   — ADMIN</li>
 *   <li>PUT  /api/amenity-bookings/{id}/cancel    — ADMIN, RESIDENT</li>
 * </ul>
 */
@RestController
@Tag(name = "Amenities", description = "Amenity management and booking lifecycle")
public class AmenityController {

    private final AmenityService amenityService;

    /**
     * Constructs the controller with the amenity service dependency.
     *
     * @param amenityService the amenity service.
     */
    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    // =========================================================================
    // Amenity endpoints — /api/amenities
    // =========================================================================

    /**
     * Lists amenities with an optional active-status filter.
     *
     * @param active    optional filter; when provided only amenities matching this flag are returned.
     * @param page      0-based page index (default 0).
     * @param size      page size (default 20, max 100).
     * @param principal the authenticated caller.
     * @return 200 OK with paginated amenity list.
     */
    @GetMapping("/api/amenities")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','RESIDENT','BOARD_MEMBER')")
    @Operation(summary = "List amenities")
    public ResponseEntity<PageResponse<AmenityResponse>> listAmenities(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize, Sort.by(Sort.Order.asc("name")));
        return ResponseEntity.ok(amenityService.listAmenities(active, pageable));
    }

    /**
     * Creates a new amenity.
     *
     * @param req       the create request body.
     * @param principal the authenticated admin.
     * @return 201 Created with the saved amenity response.
     */
    @PostMapping("/api/amenities")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create amenity")
    public ResponseEntity<AmenityResponse> createAmenity(
            @Valid @RequestBody CreateAmenityRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED).body(amenityService.createAmenity(req));
    }

    /**
     * Returns a single amenity by ID.
     *
     * @param id        the amenity UUID.
     * @param principal the authenticated caller.
     * @return 200 OK with the amenity response.
     */
    @GetMapping("/api/amenities/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get amenity by ID")
    public ResponseEntity<AmenityResponse> getAmenity(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(amenityService.getAmenity(id));
    }

    /**
     * Updates an existing amenity.
     *
     * @param id        the amenity UUID to update.
     * @param req       the update request body.
     * @param principal the authenticated admin.
     * @return 200 OK with the updated amenity response.
     */
    @PutMapping("/api/amenities/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update amenity")
    public ResponseEntity<AmenityResponse> updateAmenity(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAmenityRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(amenityService.updateAmenity(id, req));
    }

    /**
     * Deactivates an amenity (soft delete) and cancels its pending future bookings.
     *
     * @param id        the amenity UUID to deactivate.
     * @param principal the authenticated admin.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/api/amenities/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate amenity")
    public ResponseEntity<Void> deactivateAmenity(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        amenityService.deactivateAmenity(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the booked time slots for an amenity on a given date.
     *
     * @param id        the amenity UUID.
     * @param date      the date to query (required, ISO format YYYY-MM-DD).
     * @param principal the authenticated caller.
     * @return 200 OK with the availability response.
     */
    @GetMapping("/api/amenities/{id}/availability")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get amenity availability for a date")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(amenityService.getAvailability(id, date));
    }

    // =========================================================================
    // Booking endpoints — /api/amenity-bookings
    // =========================================================================

    /**
     * Lists bookings with optional filters.
     *
     * <p>Available to ADMIN, TECHNICIAN, and BOARD_MEMBER (all bookings) and RESIDENT (own bookings
     * only — the {@code residentId} param is ignored and server-side scoping is applied).
     *
     * @param amenityId  optional amenity UUID filter.
     * @param residentId optional resident UUID filter.
     * @param status     optional booking status filter.
     * @param page       0-based page index.
     * @param size       page size (max 100).
     * @param principal  the authenticated caller.
     * @return 200 OK with paginated booking list.
     */
    @GetMapping("/api/amenity-bookings")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER','RESIDENT')")
    @Operation(summary = "List amenity bookings — ADMIN/staff see all; RESIDENT sees own only")
    public ResponseEntity<PageResponse<AmenityBookingResponse>> listBookings(
            @RequestParam(required = false) UUID amenityId,
            @RequestParam(required = false) UUID residentId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("bookingDate")));
        String role = extractRole(principal);
        return ResponseEntity.ok(
                amenityService.listBookings(amenityId, residentId, status, pageable,
                        principal.getId(), role));
    }

    /**
     * Creates a new amenity booking for the authenticated resident.
     *
     * <p>When {@code amenity.requiresApproval = false} the booking is immediately APPROVED.
     *
     * @param req       the booking create request.
     * @param principal the authenticated resident or admin.
     * @return 201 Created with the booking response.
     */
    @PostMapping("/api/amenity-bookings")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Create amenity booking")
    public ResponseEntity<AmenityBookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(amenityService.createBooking(req, principal.getId()));
    }

    /**
     * Returns a single booking by ID.
     *
     * <p>RESIDENT callers receive 403 FORBIDDEN when accessing another resident's booking.
     *
     * @param id        the booking UUID.
     * @param principal the authenticated caller.
     * @return 200 OK with the booking response.
     */
    @GetMapping("/api/amenity-bookings/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<AmenityBookingResponse> getBooking(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.ok(amenityService.getBooking(id, principal.getId(), role));
    }

    /**
     * Approves or rejects a PENDING booking.
     *
     * <p>Request body must contain {@code status} set to either APPROVED or REJECTED.
     * Rejected bookings should include a {@code rejectionReason}.
     *
     * @param id        the booking UUID.
     * @param req       the approve/reject request body.
     * @param principal the authenticated admin.
     * @return 200 OK with the updated booking response.
     */
    @PutMapping("/api/amenity-bookings/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve or reject a pending booking")
    public ResponseEntity<AmenityBookingResponse> approveOrReject(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveRejectRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(amenityService.approveOrReject(id, req, principal.getId()));
    }

    /**
     * Cancels an existing booking.
     *
     * <p>RESIDENT may cancel their own PENDING or APPROVED booking before the booking date.
     * ADMIN may cancel any non-terminal booking.
     *
     * @param id        the booking UUID.
     * @param principal the authenticated caller.
     * @return 200 OK with the updated booking response.
     */
    @PutMapping("/api/amenity-bookings/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Cancel a booking")
    public ResponseEntity<AmenityBookingResponse> cancelBooking(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.ok(amenityService.cancelBooking(id, principal.getId(), role));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Extracts the role string from the principal's first authority, stripping the {@code ROLE_} prefix.
     *
     * @param principal the authenticated user principal.
     * @return the role string (e.g. {@code "ADMIN"}, {@code "RESIDENT"}).
     */
    private String extractRole(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
    }
}
