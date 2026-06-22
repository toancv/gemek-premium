/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.dto.MarkReadResponse;
import vn.vtit.gemek.module.announcement.dto.UpdateAnnouncementRequest;

import java.util.UUID;

/**
 * Service interface for the Announcements module.
 *
 * <p>Defines all operations for creating, managing, publishing, and reading announcements.
 * Role-based visibility is enforced by the implementation: ADMIN, TECHNICIAN, and BOARD_MEMBER
 * see all announcements; RESIDENT sees only published announcements within their block/floor scope.
 */
public interface AnnouncementService {

    /**
     * Returns a paginated list of announcements filtered by the caller's role and scope.
     *
     * @param principalId the caller's UUID.
     * @param role        the caller's role string.
     * @param pageable    pagination and sort parameters.
     * @return paginated announcement responses.
     */
    PageResponse<AnnouncementResponse> listAnnouncements(UUID principalId, String role, Pageable pageable);

    /**
     * Creates a new announcement as a draft (publishedAt is null).
     *
     * @param req         the create request body.
     * @param principalId the creating admin's UUID.
     * @return the saved announcement response.
     */
    AnnouncementResponse createAnnouncement(CreateAnnouncementRequest req, UUID principalId);

    /**
     * Returns the detail of a single announcement.
     *
     * <p>RESIDENT callers receive 404 if the announcement is not yet published.
     *
     * @param id          the announcement UUID.
     * @param principalId the caller's UUID.
     * @param role        the caller's role string.
     * @return the announcement response.
     */
    AnnouncementResponse getAnnouncement(UUID id, UUID principalId, String role);

    /**
     * Updates a draft announcement.
     *
     * <p>Rejects with {@code 409 CONFLICT} if the announcement has already been published.
     *
     * @param id  the announcement UUID.
     * @param req the update request body.
     * @return the updated announcement response.
     */
    AnnouncementResponse updateAnnouncement(UUID id, UpdateAnnouncementRequest req);

    /**
     * Publishes a draft announcement, making it visible to residents.
     *
     * <p>Sets {@code publishedAt} to now and logs delivery intent (stub — full delivery in Module 10).
     *
     * @param id          the announcement UUID.
     * @param principalId the publishing admin's UUID.
     * @return the updated announcement response with {@code publishedAt} set.
     */
    AnnouncementResponse publishAnnouncement(UUID id, UUID principalId);

    /**
     * Marks an announcement as read by the calling user.
     *
     * <p>Idempotent: if a read record already exists the method returns it without creating a duplicate.
     *
     * @param id          the announcement UUID.
     * @param principalId the reading user's UUID.
     * @return a response indicating whether the read was already recorded.
     */
    MarkReadResponse markRead(UUID id, UUID principalId);

    /**
     * Deletes a draft announcement.
     *
     * <p>Only ADMIN may delete. Rejects with {@code 409 CONFLICT} if the announcement is published.
     *
     * @param id the announcement UUID.
     */
    void deleteAnnouncement(UUID id);
}
