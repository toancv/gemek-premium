/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import vn.vtit.gemek.common.model.PageResponse;
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
 * Service interface for the Ticket Management module.
 *
 * <p>Defines all operations on tickets, photos, status transitions, ratings, and SLA reporting.
 */
public interface TicketService {

    /**
     * Returns a paginated, role-scoped list of tickets with optional filters.
     *
     * @param principalId UUID of the authenticated caller.
     * @param role        role string of the caller (e.g. {@code "ADMIN"}, {@code "RESIDENT"}).
     * @param visibility  optional RESIDENT list filter: {@code null}/"mine" = own household
     *                    (pre-P5 behavior), "community" = public tickets only (redacted rows
     *                    outside the caller's household). Ignored for other roles.
     * @param statuses    optional status filter; {@code null} or empty list matches all statuses.
     * @param category    optional category filter.
     * @param priority    optional priority filter.
     * @param apartmentId optional apartment filter (ADMIN/BOARD_MEMBER only).
     * @param pageable    pagination and sort parameters.
     * @return paginated summary responses.
     */
    PageResponse<TicketSummaryResponse> listTickets(UUID principalId, String role,
                                                    String visibility,
                                                    List<TicketStatus> statuses,
                                                    TicketCategory category,
                                                    TicketPriority priority,
                                                    UUID apartmentId,
                                                    Pageable pageable);

    /**
     * Creates a new ticket.
     *
     * <p>RESIDENT callers are restricted to their active apartment.
     *
     * @param req         the creation request.
     * @param principalId UUID of the authenticated caller.
     * @param role        role string of the caller.
     * @return the created ticket as a detail response.
     */
    TicketDetailResponse createTicket(CreateTicketRequest req, UUID principalId, String role);

    /**
     * Returns the full detail of a single ticket.
     *
     * <p>Access is scoped by role: RESIDENT sees only their own apartment's tickets,
     * TECHNICIAN only sees tickets assigned to them.
     *
     * @param id          the ticket UUID.
     * @param principalId UUID of the authenticated caller.
     * @param role        role string of the caller.
     * @return the ticket detail response with photos and history.
     */
    TicketDetailResponse getTicketDetail(UUID id, UUID principalId, String role);

    /**
     * Assigns a ticket to a staff user or contractor and optionally sets a scheduled date.
     *
     * @param id          the ticket UUID.
     * @param req         the assignment request.
     * @param principalId UUID of the authenticated admin.
     * @return the updated ticket as a detail response.
     */
    TicketDetailResponse assignTicket(UUID id, AssignTicketRequest req, UUID principalId);

    /**
     * Advances or cancels a ticket's status following the defined workflow graph.
     *
     * @param id          the ticket UUID.
     * @param req         the status update request.
     * @param principalId UUID of the authenticated caller.
     * @param role        role string of the caller.
     * @return the updated ticket as a detail response.
     */
    TicketDetailResponse updateStatus(UUID id, UpdateTicketStatusRequest req,
                                      UUID principalId, String role);

    /**
     * Uploads one or more photos for a ticket and persists the MinIO object keys.
     *
     * @param id          the ticket UUID.
     * @param files       multipart files to upload.
     * @param phase       the work phase the photos document.
     * @param principalId UUID of the authenticated caller.
     * @param role        role string of the caller.
     * @return list of photo response DTOs with presigned URLs.
     */
    List<TicketDetailResponse.PhotoResponse> uploadPhotos(UUID id, List<MultipartFile> files,
                                                          PhotoPhase phase,
                                                          UUID principalId, String role);

    /**
     * Deletes a single photo from a ticket and removes the object from MinIO.
     *
     * @param ticketId the parent ticket UUID.
     * @param photoId  the photo UUID to delete.
     */
    void deletePhoto(UUID ticketId, UUID photoId);

    /**
     * Records a resident satisfaction rating on a completed ticket.
     *
     * <p>Only the ticket submitter may rate, only once, and only when status is DONE.
     *
     * @param id          the ticket UUID.
     * @param req         the rating request.
     * @param principalId UUID of the authenticated resident.
     * @return the updated ticket as a detail response.
     */
    TicketDetailResponse rateTicket(UUID id, RateTicketRequest req, UUID principalId);

    /**
     * Generates the SLA report aggregated by category for a given date range.
     *
     * @param from     optional inclusive start date; {@code null} means no lower bound.
     * @param to       optional inclusive end date; {@code null} means no upper bound.
     * @param category optional category filter; {@code null} means all categories.
     * @return the SLA report response.
     */
    SlaReportResponse getSlaReport(LocalDate from, LocalDate to, TicketCategory category);

    /**
     * Asserts that the caller has read access to the ticket that owns the given photo key.
     *
     * <p>Strict household/staff rule: RESIDENT may only access photos belonging to their
     * own apartment's tickets — a public ticket grants NO photo access (F-05 gate, G8);
     * TECHNICIAN must be assigned or the ticket must be NEW; ADMIN and BOARD_MEMBER are
     * unrestricted. Deliberately stricter than {@code getTicketDetail} read access.
     *
     * <p>Throws {@link vn.vtit.gemek.common.exception.AppException} with
     * {@link vn.vtit.gemek.common.exception.ErrorCode#FORBIDDEN} if access is denied,
     * or {@code NOT_FOUND} if the key does not correspond to any recorded photo.
     *
     * @param fileUrl     the MinIO object key being presigned.
     * @param principalId UUID of the authenticated caller.
     * @param role        role string of the caller.
     */
    void assertPresignAccess(String fileUrl, UUID principalId, String role);
}
