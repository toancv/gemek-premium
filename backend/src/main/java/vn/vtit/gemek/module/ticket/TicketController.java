/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.module.ticket.dto.AssignTicketRequest;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.SlaReportResponse;
import vn.vtit.gemek.module.ticket.dto.TicketDetailResponse;
import vn.vtit.gemek.module.ticket.dto.TicketSummaryResponse;
import vn.vtit.gemek.module.ticket.dto.UpdateTicketStatusRequest;
import vn.vtit.gemek.module.ticket.entity.PhotoPhase;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Ticket Management module.
 *
 * <p>Role access per endpoint:
 * <ul>
 *   <li>GET /api/tickets — ADMIN, TECHNICIAN, RESIDENT, BOARD_MEMBER</li>
 *   <li>POST /api/tickets — ADMIN, RESIDENT</li>
 *   <li>GET /api/tickets/sla-report — ADMIN, BOARD_MEMBER</li>
 *   <li>GET /api/tickets/{id} — ADMIN, TECHNICIAN, RESIDENT, BOARD_MEMBER</li>
 *   <li>PUT /api/tickets/{id}/assign — ADMIN</li>
 *   <li>PUT /api/tickets/{id}/status — ADMIN, TECHNICIAN</li>
 *   <li>POST /api/tickets/{id}/photos — ADMIN, TECHNICIAN, RESIDENT</li>
 *   <li>POST /api/tickets/{id}/rate — RESIDENT</li>
 *   <li>DELETE /api/tickets/{id}/photos/{photoId} — ADMIN, TECHNICIAN</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Ticket management and SLA reporting")
public class TicketController {

    private final TicketService ticketService;

    /**
     * Constructs the controller with the ticket service dependency.
     *
     * @param ticketService the ticket service.
     */
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // =========================================================================
    // List
    // =========================================================================

    /**
     * Returns a paginated, role-scoped list of tickets with optional filters.
     *
     * <p>The {@code status} parameter may be repeated to filter multiple statuses
     * (e.g. {@code ?status=NEW&status=ASSIGNED}). An invalid value yields 400 VALIDATION_ERROR.
     *
     * @param status      optional status filter; may be repeated for multi-value IN filtering.
     * @param category    optional category filter.
     * @param priority    optional priority filter.
     * @param apartmentId optional apartment UUID filter (ADMIN/BOARD_MEMBER).
     * @param page        0-based page index (default 0).
     * @param size        page size (default 20, max 100).
     * @param principal   the authenticated caller.
     * @return paginated ticket summary DTOs.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','RESIDENT','BOARD_MEMBER')")
    @Operation(summary = "List tickets (role-scoped)")
    public ResponseEntity<PageResponse<TicketSummaryResponse>> listTickets(
            @RequestParam(required = false) List<TicketStatus> status,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) UUID apartmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        String role = extractRole(principal);
        return ResponseEntity.ok(ticketService.listTickets(
                principal.getId(), role, status, category, priority, apartmentId, pageable));
    }

    // =========================================================================
    // Create
    // =========================================================================

    /**
     * Creates a new ticket.
     *
     * <p>RESIDENT callers are restricted to submitting for their own active apartment.
     *
     * @param req       the create request body.
     * @param principal the authenticated caller.
     * @return 201 Created with the ticket detail DTO.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Operation(summary = "Submit a new ticket")
    public ResponseEntity<TicketDetailResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.createTicket(req, principal.getId(), role));
    }

    // =========================================================================
    // SLA report — must be declared BEFORE /{id} to avoid path ambiguity
    // =========================================================================

    /**
     * Returns the SLA report aggregated by category for the given period.
     *
     * @param from     optional inclusive start date (yyyy-MM-dd).
     * @param to       optional inclusive end date (yyyy-MM-dd).
     * @param category optional category filter.
     * @return the SLA report response.
     */
    @GetMapping("/sla-report")
    @PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER')")
    @Operation(summary = "SLA report aggregated by category")
    public ResponseEntity<SlaReportResponse> getSlaReport(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) TicketCategory category) {

        return ResponseEntity.ok(ticketService.getSlaReport(from, to, category));
    }

    // =========================================================================
    // Get single ticket
    // =========================================================================

    /**
     * Returns the full detail of a single ticket.
     *
     * @param id        the ticket UUID path variable.
     * @param principal the authenticated caller.
     * @return 200 OK with the ticket detail DTO including photos and history.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','RESIDENT','BOARD_MEMBER')")
    @Operation(summary = "Get ticket detail")
    public ResponseEntity<TicketDetailResponse> getTicket(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.ok(ticketService.getTicketDetail(id, principal.getId(), role));
    }

    // =========================================================================
    // Assign
    // =========================================================================

    /**
     * Assigns a ticket to a staff user or contractor.
     *
     * @param id        the ticket UUID path variable.
     * @param req       the assignment request body.
     * @param principal the authenticated admin.
     * @return 200 OK with the updated ticket detail DTO.
     */
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign ticket to staff or contractor")
    public ResponseEntity<TicketDetailResponse> assignTicket(
            @PathVariable UUID id,
            // SECURITY-FIX: added @Valid to trigger bean validation including @AssertTrue on DTO
            @Valid @RequestBody AssignTicketRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ticketService.assignTicket(id, req, principal.getId()));
    }

    // =========================================================================
    // Status update
    // =========================================================================

    /**
     * Advances or cancels a ticket's lifecycle status.
     *
     * @param id        the ticket UUID path variable.
     * @param req       the status update request body.
     * @param principal the authenticated caller.
     * @return 200 OK with the updated ticket detail DTO.
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Update ticket status")
    public ResponseEntity<TicketDetailResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTicketStatusRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.ok(ticketService.updateStatus(id, req, principal.getId(), role));
    }

    // =========================================================================
    // Photos
    // =========================================================================

    /**
     * Uploads one or more photos for a ticket.
     *
     * @param id        the ticket UUID path variable.
     * @param files     the multipart files to upload.
     * @param phase     the work phase the photos document.
     * @param principal the authenticated caller.
     * @return 201 Created with the list of uploaded photo response DTOs.
     */
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','RESIDENT')")
    @Operation(summary = "Upload photos for a ticket")
    public ResponseEntity<List<TicketDetailResponse.PhotoResponse>> uploadPhotos(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("phase") PhotoPhase phase,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.uploadPhotos(id, files, phase, principal.getId(), role));
    }

    /**
     * Deletes a single photo from a ticket and removes it from object storage.
     *
     * @param id        the ticket UUID path variable.
     * @param photoId   the photo UUID path variable.
     * @param principal the authenticated caller (unused here; role check via PreAuthorize).
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Delete a photo from a ticket")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal principal) {

        ticketService.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Rate
    // =========================================================================

    /**
     * Records a resident satisfaction rating on a completed ticket.
     *
     * @param id        the ticket UUID path variable.
     * @param req       the rating request body.
     * @param principal the authenticated resident.
     * @return 200 OK with the updated ticket detail DTO.
     */
    @PostMapping("/{id}/rate")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Rate a completed ticket")
    public ResponseEntity<TicketDetailResponse> rateTicket(
            @PathVariable UUID id,
            @Valid @RequestBody RateTicketRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ticketService.rateTicket(id, req, principal.getId()));
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
