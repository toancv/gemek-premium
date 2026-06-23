/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMedia;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMediaKind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AnnouncementMedia}.
 *
 * <p>All lookups are scoped by {@code announcementId} so an admin cannot address a media row
 * outside the announcement in the path (dual-key access, mirroring {@code TicketPhotoRepository}).
 */
@Repository
public interface AnnouncementMediaRepository extends JpaRepository<AnnouncementMedia, UUID> {

    /**
     * Lists all media rows of an announcement, oldest first.
     *
     * @param announcementId the owning announcement id.
     * @return the announcement's media rows.
     */
    List<AnnouncementMedia> findByAnnouncementIdOrderByCreatedAtAsc(UUID announcementId);

    /**
     * Counts the media rows of an announcement (for the ≤5-images cap).
     *
     * @param announcementId the owning announcement id.
     * @return the current media count.
     */
    long countByAnnouncementId(UUID announcementId);

    /**
     * Finds the existing cover row of an announcement, if any (for cover-replace).
     *
     * @param announcementId the owning announcement id.
     * @param kind           the media kind to match (COVER).
     * @return the cover media row, or empty.
     */
    Optional<AnnouncementMedia> findByAnnouncementIdAndKind(UUID announcementId, AnnouncementMediaKind kind);

    /**
     * Finds a media row by its id AND owning announcement id (dual-key — prevents cross-announcement delete).
     *
     * @param announcementId the owning announcement id.
     * @param id             the media row id.
     * @return the matching media row, or empty.
     */
    Optional<AnnouncementMedia> findByAnnouncementIdAndId(UUID announcementId, UUID id);

    /**
     * Sums the byte sizes of an announcement's media (for the ≤50MB total cap).
     *
     * <p>{@code COALESCE} returns 0 when the announcement has no media (or all sizes null) so the
     * caller never has to null-check the aggregate.
     *
     * @param announcementId the owning announcement id.
     * @return the total stored bytes, 0 when none.
     */
    @Query("SELECT COALESCE(SUM(m.sizeBytes), 0) FROM AnnouncementMedia m "
            + "WHERE m.announcement.id = :announcementId")
    long sumSizeBytesByAnnouncementId(@Param("announcementId") UUID announcementId);
}
