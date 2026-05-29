/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
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
 * Service interface for all parking management operations.
 *
 * <p>Covers three sub-domains: parking slot CRUD, slot assignment lifecycle,
 * and guest vehicle entry/exit logging.
 */
public interface ParkingService {

    // -------------------------------------------------------------------------
    // Parking slots
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of parking slots, optionally filtered by type, status, and zone.
     *
     * @param type     optional type filter.
     * @param status   optional status filter.
     * @param zone     optional zone substring filter.
     * @param pageable pagination and sort parameters.
     * @return paginated slot responses.
     */
    PageResponse<ParkingSlotResponse> listSlots(ParkingSlotType type, ParkingSlotStatus status,
                                                String zone, Pageable pageable);

    /**
     * Creates a new parking slot.
     *
     * @param req the creation request.
     * @return the created slot response.
     */
    ParkingSlotResponse createSlot(CreateParkingSlotRequest req);

    /**
     * Returns a single parking slot by ID.
     *
     * @param id the slot UUID.
     * @return the slot response.
     */
    ParkingSlotResponse getSlot(UUID id);

    /**
     * Updates mutable fields on a parking slot.
     *
     * @param id  the slot UUID.
     * @param req the update request.
     * @return the updated slot response.
     */
    ParkingSlotResponse updateSlot(UUID id, UpdateParkingSlotRequest req);

    /**
     * Deletes a parking slot.
     *
     * <p>Fails with {@code CONFLICT} if the slot has any assignment history.
     *
     * @param id the slot UUID.
     */
    void deleteSlot(UUID id);

    // -------------------------------------------------------------------------
    // Assignments
    // -------------------------------------------------------------------------

    /**
     * Assigns a parking slot to a vehicle, atomically setting the slot status to OCCUPIED.
     *
     * <p>Fails with {@code CONFLICT} if the slot is not AVAILABLE or already has an active
     * assignment.
     *
     * @param slotId      the parking slot UUID (from path variable).
     * @param req         the assignment creation request.
     * @param principalId the authenticated user's UUID (for audit purposes).
     * @return the created assignment response.
     */
    ParkingAssignmentResponse assignSlot(UUID slotId, CreateAssignmentRequest req, UUID principalId);

    /**
     * Ends the active assignment for a slot, atomically setting the slot status to AVAILABLE.
     *
     * <p>Fails with {@code NOT_FOUND} if no active assignment exists for the slot.
     *
     * @param slotId the parking slot UUID.
     * @param req    the unassign request carrying the optional end date.
     * @return the updated assignment response.
     */
    ParkingAssignmentResponse unassignSlot(UUID slotId, UnassignRequest req);

    /**
     * Returns a paginated list of assignments with optional filters.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param slotId      optional slot UUID filter.
     * @param active      optional active-only filter ({@code true} = end_date IS NULL).
     * @param pageable    pagination and sort parameters.
     * @return paginated assignment responses.
     */
    PageResponse<ParkingAssignmentResponse> listAssignments(UUID apartmentId, UUID slotId,
                                                            Boolean active, Pageable pageable);

    /**
     * Returns a single assignment by ID.
     *
     * @param id the assignment UUID.
     * @return the assignment response.
     */
    ParkingAssignmentResponse getAssignment(UUID id);

    // -------------------------------------------------------------------------
    // Guest vehicles
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of guest vehicle records with optional filters.
     *
     * @param apartmentId optional host apartment UUID filter.
     * @param active      optional active-only filter ({@code true} = exit_time IS NULL).
     * @param pageable    pagination and sort parameters.
     * @return paginated guest vehicle responses.
     */
    PageResponse<GuestVehicleResponse> listGuestVehicles(UUID apartmentId, Boolean active,
                                                         Pageable pageable);

    /**
     * Logs a guest vehicle entry.
     *
     * @param req         the guest vehicle creation request.
     * @param principalId the authenticated user's UUID.
     * @return the created guest vehicle response.
     */
    GuestVehicleResponse logGuestVehicle(CreateGuestVehicleRequest req, UUID principalId);

    /**
     * Records the exit of a guest vehicle.
     *
     * <p>Fails with {@code CONFLICT} if the guest vehicle has already checked out.
     *
     * @param id  the guest vehicle record UUID.
     * @param req the checkout request carrying the optional exit time.
     * @return the updated guest vehicle response.
     */
    GuestVehicleResponse checkoutGuest(UUID id, CheckoutRequest req);
}
