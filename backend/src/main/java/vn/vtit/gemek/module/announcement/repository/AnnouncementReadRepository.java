/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.announcement.entity.AnnouncementRead;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link AnnouncementRead} entity.
 *
 * <p>Supports idempotent mark-read via {@link #existsByAnnouncementIdAndUserId}
 * and read-count aggregation for the announcement detail response.
 */
@Repository
public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, UUID> {

    /**
     * Returns {@code true} if the given user has already read the given announcement.
     *
     * @param announcementId the announcement UUID.
     * @param userId         the user UUID.
     * @return {@code true} if a read record exists for this pair.
     */
    boolean existsByAnnouncementIdAndUserId(UUID announcementId, UUID userId);

    /**
     * Returns the read record for a specific user and announcement pair, if it exists.
     *
     * @param announcementId the announcement UUID.
     * @param userId         the user UUID.
     * @return an {@link Optional} containing the existing read record, or empty.
     */
    Optional<AnnouncementRead> findByAnnouncementIdAndUserId(UUID announcementId, UUID userId);

    /**
     * Returns the total number of users who have read the given announcement.
     *
     * @param announcementId the announcement UUID.
     * @return total read count.
     */
    long countByAnnouncementId(UUID announcementId);
}
