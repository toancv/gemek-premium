/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.vehicle.dto.CreateVehicleRequest;
import vn.vtit.gemek.module.vehicle.dto.UpdateVehicleRequest;
import vn.vtit.gemek.module.vehicle.dto.VehicleResponse;

import java.util.UUID;

/**
 * Business operations for managing vehicle registrations.
 */
public interface VehicleService {

    /**
     * Returns a paginated list of all vehicle records.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param search      optional case-insensitive substring matched against licensePlate, brand, and model.
     * @param pageable    pagination and sort parameters.
     * @return paginated vehicle response DTOs.
     */
    PageResponse<VehicleResponse> listVehicles(UUID apartmentId, String search, Pageable pageable);

    /**
     * Registers a new vehicle.
     *
     * <p>When {@code role} is RESIDENT, verifies that the resident belongs to the
     * caller's active apartment before creating the record.
     *
     * @param req         the create request body.
     * @param principalId UUID of the authenticated user.
     * @param role        the authenticated user's role string.
     * @return the created vehicle response DTO.
     */
    VehicleResponse createVehicle(CreateVehicleRequest req, UUID principalId, String role);

    /**
     * Returns a single vehicle record.
     *
     * <p>When {@code role} is RESIDENT, verifies that the vehicle belongs to the
     * caller's active apartment.
     *
     * @param id          the vehicle UUID.
     * @param principalId UUID of the authenticated user.
     * @param role        the authenticated user's role string.
     * @return the vehicle response DTO.
     */
    VehicleResponse getVehicle(UUID id, UUID principalId, String role);

    /**
     * Updates mutable fields on a vehicle record.
     *
     * <p>When {@code role} is RESIDENT, verifies ownership before applying changes.
     *
     * @param id          the vehicle UUID.
     * @param req         the update request body.
     * @param principalId UUID of the authenticated user.
     * @param role        the authenticated user's role string.
     * @return the updated vehicle response DTO.
     */
    VehicleResponse updateVehicle(UUID id, UpdateVehicleRequest req, UUID principalId, String role);

    /**
     * Soft-deletes a vehicle by setting {@code isActive} to {@code false}.
     *
     * <p>When {@code role} is RESIDENT, verifies ownership before deactivating.
     *
     * @param id          the vehicle UUID.
     * @param principalId UUID of the authenticated user.
     * @param role        the authenticated user's role string.
     */
    void deleteVehicle(UUID id, UUID principalId, String role);
}
