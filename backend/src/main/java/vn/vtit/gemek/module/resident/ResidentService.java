/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.dto.MoveOutRequest;
import vn.vtit.gemek.module.resident.dto.ResidentHistoryResponse;
import vn.vtit.gemek.module.resident.dto.ResidentLookupResponse;
import vn.vtit.gemek.module.resident.dto.ResidentResponse;
import vn.vtit.gemek.module.resident.dto.UpdateResidentRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;

import java.util.List;
import java.util.UUID;

/**
 * Business operations for managing resident records and their history.
 */
public interface ResidentService {

    /**
     * Returns ALL active resident records for the authenticated user.
     *
     * <p>The caller's identity is derived from the JWT principal; no client-supplied
     * identifier is accepted to prevent IDOR attacks.
     *
     * <p>A user may hold multiple concurrent active residencies (multi-residency), so this
     * returns a list. An empty list (no active residency — e.g. between move-out and a return)
     * is a valid state and is returned as {@code []}, never an error.
     *
     * @param userId UUID of the authenticated user derived from the JWT principal.
     * @return all active resident response DTOs for the user (possibly empty), primary first.
     */
    List<ResidentResponse> getMyResident(UUID userId);

    /**
     * Returns a paginated list of residents filtered by optional criteria.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param type        optional resident type filter.
     * @param isActive    when {@code true} returns only active residents (no move-out date);
     *                    when {@code false} returns only past residents; when {@code null} returns all.
     * @param search      optional case-insensitive substring matched against the resident's user
     *                    full name and email; {@code null} or blank means no filter.
     * @param pageable    pagination and sort parameters.
     * @return paginated resident response DTOs.
     */
    PageResponse<ResidentResponse> listResidents(UUID apartmentId, ResidentType type,
                                                  Boolean isActive, String search, Pageable pageable);

    /**
     * Resolves a phone (and optional target apartment) to a place-resident branch status plus the minimum
     * identifying info for an admin to recognize the person.
     *
     * <p>ADMIN-only, read-only. Returns NEW (phone unused), ACTIVE_ELSEWHERE (active in other apartment(s)),
     * MOVED_OUT (exists, no active residency), or — when {@code apartmentId} is supplied — ALREADY_HERE.
     * Returns only display name + active-apartment identifiers; never full PII.
     *
     * @param phone       the phone to resolve (any VN format; normalized server-side).
     * @param apartmentId optional target apartment to additionally detect the ALREADY_HERE case; may be null.
     * @return the lookup result.
     */
    ResidentLookupResponse lookupByPhone(String phone, UUID apartmentId);

    /**
     * Places a resident into an apartment, branching on phone: provisions a new user+residency for an
     * unknown phone, or REUSES an existing user (adding a residency, reactivating a disabled account) when
     * the phone is known and {@code confirmReuse=true}. Writes a MOVED_IN history entry in the same
     * transaction.
     *
     * @param req         the place request body.
     * @param principalId UUID of the authenticated admin performing the action.
     * @return the created resident response DTO.
     */
    ResidentResponse createResident(CreateResidentRequest req, UUID principalId);

    /**
     * Returns a single resident record.
     *
     * <p>When {@code role} is RESIDENT the record is only returned if
     * {@code principalId} matches the resident's user.
     *
     * @param id          the resident UUID.
     * @param principalId UUID of the authenticated user.
     * @param role        the authenticated user's role string (e.g., "ADMIN", "RESIDENT").
     * @return the resident response DTO.
     */
    ResidentResponse getResident(UUID id, UUID principalId, String role);

    /**
     * Updates mutable fields on a resident record.
     *
     * <p>Writes a TYPE_CHANGED history entry when the type changes, and a
     * PRIMARY_CONTACT_SET entry when {@code isPrimaryContact} transitions to {@code true}.
     *
     * @param id          the resident UUID.
     * @param req         the update request body.
     * @param principalId UUID of the authenticated user performing the action.
     * @return the updated resident response DTO.
     */
    ResidentResponse updateResident(UUID id, UpdateResidentRequest req, UUID principalId);

    /**
     * Records a move-out for the resident and writes a MOVED_OUT history entry.
     *
     * @param id          the resident UUID.
     * @param req         the move-out request body containing the move-out date.
     * @param principalId UUID of the authenticated user performing the action.
     * @return the updated resident response DTO.
     */
    ResidentResponse moveOut(UUID id, MoveOutRequest req, UUID principalId);

    /**
     * Returns paginated history for a specific resident record.
     *
     * @param residentId the resident UUID.
     * @param pageable   pagination parameters.
     * @return paginated history response DTOs.
     */
    PageResponse<ResidentHistoryResponse> getResidentHistory(UUID residentId, Pageable pageable);

    /**
     * Returns paginated history for all residents ever associated with an apartment.
     *
     * @param apartmentId the apartment UUID.
     * @param pageable    pagination parameters.
     * @return paginated history response DTOs.
     */
    PageResponse<ResidentHistoryResponse> getApartmentHistory(UUID apartmentId, Pageable pageable);
}
