/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident;

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
 * REST controller for resident management and history endpoints.
 *
 * <p>ADMIN role has access to all operations. RESIDENT role may only read their
 * own record; ownership enforcement is delegated to the service layer.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Residents", description = "Resident management and history")
public class ResidentController {

    private final ResidentService residentService;

    /**
     * Constructs the controller with the resident service dependency.
     *
     * @param residentService the resident service.
     */
    public ResidentController(ResidentService residentService) {
        this.residentService = residentService;
    }

    /**
     * Returns ALL active resident records for the authenticated user.
     *
     * <p>The user identity is derived exclusively from the JWT principal.
     * No user identifier is accepted as a request parameter.
     *
     * <p>A user may hold multiple concurrent active residencies (multi-residency). The response is a
     * list ordered primary-contact first. An empty list (logged-in user with no active residency) is a
     * valid state and is returned as {@code 200 []} — NOT a 404.
     *
     * @param principal the authenticated resident principal.
     * @return 200 OK with the list of active resident response DTOs (possibly empty).
     */
    @GetMapping("/residents/me")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Get the authenticated resident's own active residencies")
    public ResponseEntity<List<ResidentResponse>> getMyResident(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(residentService.getMyResident(principal.getId()));
    }

    /**
     * Returns a paginated list of residents with optional filters.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param type        optional resident type filter.
     * @param isActive    optional active status filter.
     * @param search      optional case-insensitive substring matched against the resident user's
     *                    full name or email; {@code null} or blank disables the filter.
     * @param page        0-based page index (default 0).
     * @param size        page size (default 20, max 100).
     * @return paginated resident response DTOs.
     */
    @GetMapping("/residents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List residents with optional filters")
    public ResponseEntity<PageResponse<ResidentResponse>> listResidents(
            @RequestParam(required = false) UUID apartmentId,
            @RequestParam(required = false) ResidentType type,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.asc("apartment.unitNumber"), Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return ResponseEntity.ok(residentService.listResidents(apartmentId, type, isActive, search, pageable));
    }

    /**
     * Resolves a phone (and optional target apartment) to a place-resident branch status plus the minimum
     * info for an admin to recognize the person. Read-only, ADMIN-only. Step 1 of the two-step place flow;
     * the server still re-resolves the phone at place-time (never trusts this lookup).
     *
     * @param phone       the phone to resolve (any VN format; normalized server-side).
     * @param apartmentId optional target apartment, enabling ALREADY_HERE detection; may be omitted.
     * @return 200 OK with the lookup result (status + display name + active apartments).
     */
    @GetMapping("/residents/lookup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Look up a resident by phone for the place-resident flow")
    public ResponseEntity<ResidentLookupResponse> lookupByPhone(
            @RequestParam String phone,
            @RequestParam(required = false) UUID apartmentId) {
        return ResponseEntity.ok(residentService.lookupByPhone(phone, apartmentId));
    }

    /**
     * Places a resident into an apartment, branching on phone (new user vs reuse existing).
     *
     * @param req       the place request body.
     * @param principal the authenticated admin principal.
     * @return 201 Created with the new resident response DTO.
     */
    @PostMapping("/residents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Place a resident (create new or reuse existing user by phone)")
    public ResponseEntity<ResidentResponse> createResident(
            @Valid @RequestBody CreateResidentRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(residentService.createResident(req, principal.getId()));
    }

    /**
     * Returns a single resident record.
     *
     * <p>RESIDENT callers are restricted by the service layer to their own record.
     *
     * @param id        the resident UUID path variable.
     * @param principal the authenticated user principal.
     * @return 200 OK with the resident response DTO.
     */
    @GetMapping("/residents/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Get a resident record")
    public ResponseEntity<ResidentResponse> getResident(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        // Derive the role string from the principal's first authority.
        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");

        return ResponseEntity.ok(residentService.getResident(id, principal.getId(), role));
    }

    /**
     * Updates mutable fields on a resident record.
     *
     * @param id        the resident UUID path variable.
     * @param req       the update request body.
     * @param principal the authenticated admin principal.
     * @return 200 OK with the updated resident response DTO.
     */
    @PutMapping("/residents/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a resident record")
    public ResponseEntity<ResidentResponse> updateResident(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateResidentRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(residentService.updateResident(id, req, principal.getId()));
    }

    /**
     * Records a resident move-out.
     *
     * @param id        the resident UUID path variable.
     * @param req       the move-out request body.
     * @param principal the authenticated admin principal.
     * @return 200 OK with the updated resident response DTO.
     */
    @PostMapping("/residents/{id}/move-out")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Record a resident move-out")
    public ResponseEntity<ResidentResponse> moveOut(
            @PathVariable UUID id,
            @Valid @RequestBody MoveOutRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(residentService.moveOut(id, req, principal.getId()));
    }

    /**
     * Returns paginated history for a specific resident.
     *
     * @param id   the resident UUID path variable.
     * @param page 0-based page index (default 0).
     * @param size page size (default 20, max 100).
     * @return paginated history response DTOs.
     */
    @GetMapping("/residents/{id}/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get history for a resident")
    public ResponseEntity<PageResponse<ResidentHistoryResponse>> getResidentHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return ResponseEntity.ok(residentService.getResidentHistory(id, pageable));
    }

    /**
     * Returns paginated history for all residents ever associated with an apartment.
     *
     * @param apartmentId the apartment UUID path variable.
     * @param page        0-based page index (default 0).
     * @param size        page size (default 20, max 100).
     * @return paginated history response DTOs.
     */
    @GetMapping("/apartments/{apartmentId}/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get resident history for an apartment")
    public ResponseEntity<PageResponse<ResidentHistoryResponse>> getApartmentHistory(
            @PathVariable UUID apartmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return ResponseEntity.ok(residentService.getApartmentHistory(apartmentId, pageable));
    }
}
