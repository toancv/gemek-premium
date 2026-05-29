/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.apartment.dto.ApartmentDetailResponse;
import vn.vtit.gemek.module.apartment.dto.ApartmentSummaryResponse;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateApartmentRequest;
import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;

import java.util.UUID;

/**
 * Service interface for apartment management operations.
 *
 * <p>Defines the contract for creating, reading, updating, and deleting apartments.
 * The detail endpoint also returns active residents and registered vehicles.
 */
public interface ApartmentService {

    /**
     * Returns a paginated list of apartments with optional filters.
     *
     * @param blockId  optional block UUID filter.
     * @param floor    optional floor filter.
     * @param status   optional status filter.
     * @param search   optional unit number substring search.
     * @param pageable pagination and sort parameters.
     * @return paginated list of apartment summary DTOs.
     */
    PageResponse<ApartmentSummaryResponse> listApartments(
            UUID blockId, Short floor, ApartmentStatus status, String search, Pageable pageable);

    /**
     * Creates a new apartment.
     *
     * @param request the create request.
     * @return the created apartment summary DTO.
     * @throws vn.vtit.gemek.common.exception.AppException with NOT_FOUND if the block does not exist.
     * @throws vn.vtit.gemek.common.exception.AppException with CONFLICT if the unit number already exists in the block.
     */
    ApartmentSummaryResponse createApartment(CreateApartmentRequest request);

    /**
     * Returns the full detail of an apartment including active residents and vehicles.
     *
     * <p>When the calling user has the RESIDENT role, access is restricted to the apartment
     * they are currently assigned to.
     *
     * @param id              the apartment UUID.
     * @param requestingUserId the UUID of the authenticated user making the request.
     * @param isResident      {@code true} if the requesting user has the RESIDENT role.
     * @return the apartment detail DTO.
     * @throws vn.vtit.gemek.common.exception.AppException with NOT_FOUND if the apartment does not exist.
     * @throws vn.vtit.gemek.common.exception.AppException with FORBIDDEN if a RESIDENT accesses another apartment.
     */
    ApartmentDetailResponse getApartmentDetail(UUID id, UUID requestingUserId, boolean isResident);

    /**
     * Updates an existing apartment.
     *
     * @param id      the UUID of the apartment to update.
     * @param request the update request.
     * @return the updated apartment summary DTO.
     * @throws vn.vtit.gemek.common.exception.AppException with NOT_FOUND if the apartment does not exist.
     * @throws vn.vtit.gemek.common.exception.AppException with CONFLICT if the unit number conflicts within the block.
     */
    ApartmentSummaryResponse updateApartment(UUID id, UpdateApartmentRequest request);

    /**
     * Deletes an apartment by UUID.
     *
     * @param id the UUID of the apartment to delete.
     * @throws vn.vtit.gemek.common.exception.AppException with NOT_FOUND if the apartment does not exist.
     * @throws vn.vtit.gemek.common.exception.AppException with CONFLICT if the apartment has active residents.
     */
    void deleteApartment(UUID id);
}
