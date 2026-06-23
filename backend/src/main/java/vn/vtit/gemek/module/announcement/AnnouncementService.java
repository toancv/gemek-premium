/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.announcement.dto.AnnouncementMediaResponse;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.dto.MarkReadResponse;
import vn.vtit.gemek.module.announcement.dto.UpdateAnnouncementRequest;

import java.util.List;
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
     * MinIO object-key prefix for announcement media (cover/inline images, attachments).
     *
     * <p>Key convention — DEFINED by C2.1, consumed by the upload path (C2.2):
     * {@code announcements/{announcementId}/{uuid-filename}}. The announcement id is the FIRST
     * path segment after this prefix, so {@link #assertMediaPresignAccess} can recover it from the
     * key alone and mirror the owning announcement's read scope. This is the single source of the
     * convention; the presign dispatcher routes on this same constant.
     */
    String MEDIA_KEY_PREFIX = "announcements/";

    /**
     * Authorizes a presign request for an announcement media object, mirroring the owning
     * announcement's own read scope — the direct analogue of the ticket-photo presign gate
     * ({@code enforcePhotoAccess}), enforced independently of the (widened) announcement-LIST rule.
     *
     * <p>The owning announcement id is parsed from {@code objectKey} per {@link #MEDIA_KEY_PREFIX}.
     * Access rule:
     * <ul>
     *   <li>ADMIN / BOARD_MEMBER → allowed (unrestricted), matching announcement read.</li>
     *   <li>RESIDENT → allowed iff the announcement is PUBLISHED and its ALL/BLOCK/FLOOR scope
     *       matches one of the caller's ACTIVE residencies (the SAME predicate as the resident feed).</li>
     *   <li>Any other role → denied.</li>
     * </ul>
     * A DRAFT announcement's media is visible to ADMIN/BOARD only (drafts have no resident recipients).
     * A malformed key, or a key referencing a nonexistent announcement, is DENIED — never a 500.
     *
     * @param objectKey   the MinIO object key being presigned (must start with {@link #MEDIA_KEY_PREFIX}).
     * @param principalId the calling user's UUID.
     * @param role        the caller's role name (no {@code ROLE_} prefix).
     * @throws vn.vtit.gemek.common.exception.AppException with {@code FORBIDDEN} when access is denied.
     */
    void assertMediaPresignAccess(String objectKey, UUID principalId, String role);

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
     * Deletes a draft announcement, plus its media rows (DB cascade) and MinIO objects
     * (scheduled AFTER-COMMIT).
     *
     * <p>Only ADMIN may delete. Rejects with {@code 409 CONFLICT} if the announcement is published.
     *
     * @param id the announcement UUID.
     */
    void deleteAnnouncement(UUID id);

    // =========================================================================
    // Media (C2.2) — ADMIN, drafts only
    // =========================================================================

    /**
     * Uploads one image to a DRAFT announcement.
     *
     * <p>ADMIN only; the announcement MUST be a draft (published = immutable). The file's real
     * content type is validated by Tika on the bytes (jpg/png/webp only — the client header and
     * filename extension are NOT trusted). Per-announcement caps are enforced inside the
     * transaction: ≤5 images AND ≤50MB total. {@code kind=cover} REPLACES any existing cover
     * (old row removed + old object scheduled for after-commit delete); {@code kind=inline} may
     * repeat within the caps. The object key follows the C2.1 convention
     * {@code announcements/{announcementId}/{uuid}}.
     *
     * @param announcementId the owning announcement id.
     * @param file           the uploaded image.
     * @param kind           the media kind, {@code "cover"} or {@code "inline"} (case-insensitive).
     * @param principalId    the uploading admin's UUID.
     * @return the persisted media metadata (no presigned URL).
     */
    AnnouncementMediaResponse uploadMedia(UUID announcementId, MultipartFile file, String kind, UUID principalId);

    /**
     * Lists the media rows of an announcement (ADMIN/BOARD authoring view).
     *
     * @param announcementId the owning announcement id.
     * @return the announcement's media metadata, oldest first.
     */
    List<AnnouncementMediaResponse> listMedia(UUID announcementId);

    /**
     * Deletes one media row from a DRAFT announcement.
     *
     * <p>ADMIN only; the announcement MUST be a draft. The DB row is removed inside the
     * transaction and the MinIO object is scheduled for best-effort AFTER-COMMIT delete.
     *
     * @param announcementId the owning announcement id.
     * @param mediaId        the media row id (must belong to the announcement).
     */
    void deleteMedia(UUID announcementId, UUID mediaId);
}
