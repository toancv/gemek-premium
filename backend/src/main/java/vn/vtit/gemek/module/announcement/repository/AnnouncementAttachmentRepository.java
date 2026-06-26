/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.announcement.entity.AnnouncementAttachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AnnouncementAttachment}.
 *
 * <p>All lookups are scoped by {@code announcementId} so an admin cannot address an attachment row
 * outside the announcement in the path (dual-key access, mirroring {@code AnnouncementMediaRepository}).
 */
@Repository
public interface AnnouncementAttachmentRepository extends JpaRepository<AnnouncementAttachment, UUID> {

    /**
     * Lists all attachment rows of an announcement, oldest first.
     *
     * @param announcementId the owning announcement id.
     * @return the announcement's attachment rows.
     */
    List<AnnouncementAttachment> findByAnnouncementIdOrderByCreatedAtAsc(UUID announcementId);

    /**
     * Counts the attachment rows of an announcement (for the ≤5-attachments cap).
     *
     * @param announcementId the owning announcement id.
     * @return the current attachment count.
     */
    long countByAnnouncementId(UUID announcementId);

    /**
     * Finds an attachment row by its id AND owning announcement id (dual-key — prevents cross-announcement delete).
     *
     * @param announcementId the owning announcement id.
     * @param id             the attachment row id.
     * @return the matching attachment row, or empty.
     */
    Optional<AnnouncementAttachment> findByAnnouncementIdAndId(UUID announcementId, UUID id);

    /**
     * Sums the byte sizes of an announcement's attachments (for the ≤50MB total cap).
     *
     * <p>{@code COALESCE} returns 0 when the announcement has no attachments (or all sizes null) so the
     * caller never has to null-check the aggregate.
     *
     * @param announcementId the owning announcement id.
     * @return the total stored bytes, 0 when none.
     */
    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM AnnouncementAttachment a "
            + "WHERE a.announcement.id = :announcementId")
    long sumSizeBytesByAnnouncementId(@Param("announcementId") UUID announcementId);
}
