/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
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
 * Service contract for the Amenity Booking module.
 *
 * <p>Covers amenity CRUD, availability calendar, and the full booking lifecycle
 * (create, list, get, approve/reject, cancel).
 */
public interface AmenityService {

    // =========================================================================
    // Amenity CRUD
    // =========================================================================

    /**
     * Returns a paginated list of amenities, optionally filtered by active status.
     *
     * @param active   filter by active flag; {@code null} returns all.
     * @param pageable pagination and sort parameters.
     * @return paginated list of amenity responses.
     */
    PageResponse<AmenityResponse> listAmenities(Boolean active, Pageable pageable);

    /**
     * Creates a new amenity.
     *
     * @param req the create request containing amenity details.
     * @return the created amenity response.
     */
    AmenityResponse createAmenity(CreateAmenityRequest req);

    /**
     * Returns a single amenity by ID.
     *
     * @param id the amenity UUID.
     * @return the amenity response.
     */
    AmenityResponse getAmenity(UUID id);

    /**
     * Updates an existing amenity.
     *
     * @param id  the amenity UUID to update.
     * @param req the update request with new field values.
     * @return the updated amenity response.
     */
    AmenityResponse updateAmenity(UUID id, UpdateAmenityRequest req);

    /**
     * Deactivates an amenity by setting {@code isActive = false}.
     *
     * <p>All pending future bookings for this amenity are cancelled as a side effect.
     *
     * @param id the amenity UUID to deactivate.
     */
    void deactivateAmenity(UUID id);

    // =========================================================================
    // Availability
    // =========================================================================

    /**
     * Returns the booked time slots and operating hours for an amenity on a specific date.
     *
     * @param amenityId the amenity UUID.
     * @param date      the date to query.
     * @return the availability response with booked slot list.
     */
    AvailabilityResponse getAvailability(UUID amenityId, LocalDate date);

    // =========================================================================
    // Booking lifecycle
    // =========================================================================

    /**
     * Creates a new booking for the authenticated resident.
     *
     * <p>Enforces daily limit and slot conflict checks before persisting.
     * Auto-approves if {@code amenity.requiresApproval = false}.
     *
     * @param req         the booking creation request.
     * @param principalId the authenticated user's UUID.
     * @return the created booking response.
     */
    AmenityBookingResponse createBooking(CreateBookingRequest req, UUID principalId);

    /**
     * Returns a paginated list of bookings, optionally filtered by amenity, resident, or status.
     *
     * <p>Intended for ADMIN, TECHNICIAN, and BOARD_MEMBER roles.
     *
     * @param amenityId   optional amenity UUID filter.
     * @param residentId  optional resident UUID filter (ignored when role is RESIDENT — server
     *                    scopes to the caller's own resident record).
     * @param status      optional status filter.
     * @param pageable    pagination and sort parameters.
     * @param principalId UUID of the authenticated user.
     * @param role        role of the authenticated user.
     * @return paginated list of booking responses.
     */
    PageResponse<AmenityBookingResponse> listBookings(UUID amenityId, UUID residentId,
                                                       BookingStatus status, Pageable pageable,
                                                       UUID principalId, String role);

    /**
     * Returns a single booking by ID.
     *
     * <p>RESIDENT callers may only retrieve their own bookings; returns FORBIDDEN otherwise.
     *
     * @param id          the booking UUID.
     * @param principalId the authenticated user's UUID.
     * @param role        the caller's role string.
     * @return the booking response.
     */
    AmenityBookingResponse getBooking(UUID id, UUID principalId, String role);

    /**
     * Approves or rejects a PENDING booking.
     *
     * <p>On APPROVED: sets {@code approvedBy} and {@code approvedAt}.
     * On REJECTED: sets {@code rejectionReason}.
     *
     * @param id          the booking UUID.
     * @param req         the approve/reject request.
     * @param principalId the admin user's UUID.
     * @return the updated booking response.
     */
    AmenityBookingResponse approveOrReject(UUID id, ApproveRejectRequest req, UUID principalId);

    /**
     * Cancels an existing booking.
     *
     * <p>RESIDENT may cancel their own booking when status is PENDING or APPROVED
     * and the booking date is in the future. ADMIN may cancel any booking.
     *
     * @param id          the booking UUID.
     * @param principalId the authenticated user's UUID.
     * @param role        the caller's role string.
     * @return the updated booking response.
     */
    AmenityBookingResponse cancelBooking(UUID id, UUID principalId, String role);
}
