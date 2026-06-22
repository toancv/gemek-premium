/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

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
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.dto.MarkReadResponse;
import vn.vtit.gemek.module.announcement.dto.UpdateAnnouncementRequest;

import java.util.UUID;

/**
 * REST controller for the Announcements module.
 *
 * <p>Role access per endpoint:
 * <ul>
 *   <li>GET  /api/announcements              — ADMIN, TECHNICIAN, BOARD_MEMBER, RESIDENT</li>
 *   <li>POST /api/announcements              — ADMIN</li>
 *   <li>GET  /api/announcements/{id}         — ADMIN, TECHNICIAN, BOARD_MEMBER, RESIDENT</li>
 *   <li>PUT  /api/announcements/{id}         — ADMIN</li>
 *   <li>DELETE /api/announcements/{id}       — ADMIN</li>
 *   <li>POST /api/announcements/{id}/publish — ADMIN</li>
 *   <li>POST /api/announcements/{id}/read    — any authenticated user</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/announcements")
@Tag(name = "Announcements", description = "Announcement management and resident read-tracking")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /**
     * Constructs the controller with the announcements service dependency.
     *
     * @param announcementService the announcement service.
     */
    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    // =========================================================================
    // List
    // =========================================================================

    /**
     * Returns a paginated, role-scoped list of announcements.
     *
     * <p>ADMIN, TECHNICIAN, and BOARD_MEMBER see all announcements.
     * RESIDENT sees only published announcements within their block/floor scope.
     *
     * @param page      0-based page index (default 0).
     * @param size      page size (default 20, max 100).
     * @param principal the authenticated caller.
     * @return paginated announcement responses.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER','RESIDENT')")
    @Operation(summary = "List announcements (role-scoped)")
    public ResponseEntity<PageResponse<AnnouncementResponse>> listAnnouncements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        String role = extractRole(principal);
        return ResponseEntity.ok(
                announcementService.listAnnouncements(principal.getId(), role, pageable));
    }

    // =========================================================================
    // Create
    // =========================================================================

    /**
     * Creates a new announcement draft.
     *
     * <p>The announcement is saved with {@code publishedAt} null and becomes
     * visible to residents only after the publish endpoint is called.
     *
     * @param req       the create request body.
     * @param principal the authenticated admin.
     * @return 201 Created with the saved announcement response.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new announcement draft")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(
            @Valid @RequestBody CreateAnnouncementRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(announcementService.createAnnouncement(req, principal.getId()));
    }

    // =========================================================================
    // Get single
    // =========================================================================

    /**
     * Returns the detail of a single announcement.
     *
     * <p>RESIDENT callers receive 404 for unpublished (draft) announcements.
     *
     * @param id        the announcement UUID path variable.
     * @param principal the authenticated caller.
     * @return 200 OK with the announcement response.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER','RESIDENT')")
    @Operation(summary = "Get announcement detail")
    public ResponseEntity<AnnouncementResponse> getAnnouncement(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        String role = extractRole(principal);
        return ResponseEntity.ok(
                announcementService.getAnnouncement(id, principal.getId(), role));
    }

    // =========================================================================
    // Update
    // =========================================================================

    /**
     * Updates a draft announcement.
     *
     * <p>Returns 409 CONFLICT if the announcement has already been published.
     *
     * @param id        the announcement UUID path variable.
     * @param req       the update request body.
     * @param principal the authenticated admin.
     * @return 200 OK with the updated announcement response.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a draft announcement")
    public ResponseEntity<AnnouncementResponse> updateAnnouncement(
            @PathVariable UUID id,
            @RequestBody UpdateAnnouncementRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(announcementService.updateAnnouncement(id, req));
    }

    // =========================================================================
    // Delete
    // =========================================================================

    /**
     * Deletes a draft announcement.
     *
     * <p>Returns 409 CONFLICT if the announcement has been published.
     *
     * @param id        the announcement UUID path variable.
     * @param principal the authenticated admin.
     * @return 204 No Content on success.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a draft announcement")
    public ResponseEntity<Void> deleteAnnouncement(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        announcementService.deleteAnnouncement(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Publish
    // =========================================================================

    /**
     * Publishes a draft announcement, making it visible to all scoped residents.
     *
     * <p>Idempotent: publishing an already-published announcement returns 200 without error.
     *
     * @param id        the announcement UUID path variable.
     * @param principal the authenticated admin.
     * @return 200 OK with the updated announcement response including {@code publishedAt}.
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish a draft announcement")
    public ResponseEntity<AnnouncementResponse> publishAnnouncement(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(
                announcementService.publishAnnouncement(id, principal.getId()));
    }

    // =========================================================================
    // Mark read
    // =========================================================================

    /**
     * Marks an announcement as read by the calling user.
     *
     * <p>Idempotent: repeated calls return {@code alreadyRead=true} without creating a duplicate record.
     *
     * @param id        the announcement UUID path variable.
     * @param principal the authenticated user (any role).
     * @return 200 OK with the mark-read response.
     */
    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark an announcement as read")
    public ResponseEntity<MarkReadResponse> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(announcementService.markRead(id, principal.getId()));
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
