/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.dto.MarkReadResponse;
import vn.vtit.gemek.module.announcement.dto.UpdateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementRead;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.repository.AnnouncementReadRepository;
import vn.vtit.gemek.module.announcement.repository.AnnouncementRepository;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link AnnouncementService}.
 *
 * <p>All write operations are individually transactional. Read operations run under
 * a class-level {@code readOnly} transaction to skip dirty-checking overhead.
 *
 * <p>Notification delivery (push/email/SMS) is stubbed: the intent is logged at INFO
 * level and full dispatch will be wired in Module 10.
 */
@Service
@Transactional(readOnly = true)
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementServiceImpl.class);

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ResidentRepository residentRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Constructs the service with all required dependencies via constructor injection.
     *
     * @param announcementRepository     the announcement JPA repository.
     * @param announcementReadRepository the announcement read JPA repository.
     * @param blockRepository            the block JPA repository.
     * @param userRepository             the user JPA repository.
     * @param residentRepository         the resident JPA repository.
     * @param notificationRepository     the notification JPA repository for publish dispatch.
     */
    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository,
                                   AnnouncementReadRepository announcementReadRepository,
                                   BlockRepository blockRepository,
                                   UserRepository userRepository,
                                   ResidentRepository residentRepository,
                                   NotificationRepository notificationRepository) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.residentRepository = residentRepository;
        this.notificationRepository = notificationRepository;
    }

    // =========================================================================
    // Read operations
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>ADMIN, TECHNICIAN, and BOARD_MEMBER receive all announcements (draft + published).
     * RESIDENT receives only published announcements scoped to their active apartment's block/floor.
     * A RESIDENT with no active residency receives an empty page.
     */
    @Override
    public PageResponse<AnnouncementResponse> listAnnouncements(UUID principalId,
                                                                 String role,
                                                                 Pageable pageable) {
        log.debug("listAnnouncements — role={}", role);

        if ("RESIDENT".equals(role)) {
            // Residents see only published announcements for their block/floor.
            java.util.Optional<Resident> residentOpt = residentRepository.findActiveByUserId(principalId);
            if (residentOpt.isEmpty()) {
                // No active residency — return empty page rather than an error.
                return PageResponse.of(Page.empty(pageable));
            }
            Resident resident = residentOpt.get();
            Apartment apartment = resident.getApartment();
            UUID blockId = apartment.getBlock().getId();
            short floor = apartment.getFloor();

            Page<Announcement> residentPage = announcementRepository
                    .findPublishedForApartment(blockId, floor, pageable);
            // One batched read-state query per page — avoids N exists() calls (N+1).
            Set<UUID> readIds = readAnnouncementIds(principalId, residentPage.getContent());
            // One batched creator-name query per page — avoids N user lookups (N+1).
            Map<UUID, String> creatorNames = resolveCreatorNames(residentPage.getContent());
            return PageResponse.of(residentPage.map(
                    a -> toResponse(a, readIds.contains(a.getId()), creatorNames)));
        }

        // ADMIN, TECHNICIAN, BOARD_MEMBER — all announcements.
        Page<Announcement> adminPage = announcementRepository.findAll(pageable);
        Set<UUID> readIds = readAnnouncementIds(principalId, adminPage.getContent());
        Map<UUID, String> creatorNames = resolveCreatorNames(adminPage.getContent());
        return PageResponse.of(adminPage.map(
                a -> toResponse(a, readIds.contains(a.getId()), creatorNames)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnouncementResponse getAnnouncement(UUID id, UUID principalId, String role) {
        log.debug("getAnnouncement — id={}, role={}", id, role);

        Announcement announcement = requireAnnouncement(id);

        // RESIDENT may only view published announcements.
        if ("RESIDENT".equals(role) && announcement.getPublishedAt() == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "Announcement not found: " + id);
        }

        // Single-row detail — one exists() check is the batched query's degenerate case.
        boolean isRead = announcementReadRepository.existsByAnnouncementIdAndUserId(id, principalId);
        return toResponse(announcement, isRead);
    }

    // =========================================================================
    // Write operations
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Validates scope constraints before persisting. The announcement is saved
     * as a draft with {@code publishedAt} left null.
     */
    @Override
    @Transactional
    public AnnouncementResponse createAnnouncement(CreateAnnouncementRequest req, UUID principalId) {
        log.debug("createAnnouncement — type={}, scope={}", req.getType(), req.getTargetScope());

        validateScopeConstraints(req.getTargetScope(), req.getTargetBlockId(), req.getTargetFloor());

        Announcement announcement = new Announcement();
        announcement.setTitle(req.getTitle());
        announcement.setContent(req.getContent());
        announcement.setType(req.getType());
        announcement.setScope(req.getTargetScope());
        announcement.setSendPush(req.getSendPush() != null ? req.getSendPush() : true);
        announcement.setSendEmail(req.getSendEmail() != null ? req.getSendEmail() : false);
        announcement.setSendSms(req.getSendSms() != null ? req.getSendSms() : false);
        // createdBy is set by Spring Data auditing from the authenticated actor — no manual write.
        // publishedAt intentionally null — announcement starts as a draft.

        if (req.getTargetBlockId() != null) {
            Block block = blockRepository.findById(req.getTargetBlockId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                            "Block not found: " + req.getTargetBlockId()));
            announcement.setTargetBlock(block);
        }

        if (req.getTargetFloor() != null) {
            announcement.setTargetFloor(req.getTargetFloor());
        }

        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement created as draft — id={}", saved.getId());
        return toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only draft announcements (publishedAt == null) may be updated.
     */
    @Override
    @Transactional
    public AnnouncementResponse updateAnnouncement(UUID id, UpdateAnnouncementRequest req) {
        log.debug("updateAnnouncement — id={}", id);

        Announcement announcement = requireAnnouncement(id);

        // Published announcements are immutable.
        if (announcement.getPublishedAt() != null) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot edit a published announcement.");
        }

        // Apply only non-null fields from the request.
        if (req.getTitle() != null) {
            announcement.setTitle(req.getTitle());
        }
        if (req.getContent() != null) {
            announcement.setContent(req.getContent());
        }
        if (req.getType() != null) {
            announcement.setType(req.getType());
        }

        // If scope or targeting fields change, re-validate the combination.
        AnnouncementScope newScope = req.getTargetScope() != null ? req.getTargetScope() : announcement.getScope();
        UUID newBlockId = req.getTargetBlockId() != null
                ? req.getTargetBlockId()
                : (announcement.getTargetBlock() != null ? announcement.getTargetBlock().getId() : null);
        Short newFloor = req.getTargetFloor() != null ? req.getTargetFloor() : announcement.getTargetFloor();

        validateScopeConstraints(newScope, newBlockId, newFloor);

        if (req.getTargetScope() != null) {
            announcement.setScope(req.getTargetScope());
        }

        if (req.getTargetBlockId() != null) {
            Block block = blockRepository.findById(req.getTargetBlockId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                            "Block not found: " + req.getTargetBlockId()));
            announcement.setTargetBlock(block);
        }

        if (req.getTargetFloor() != null) {
            announcement.setTargetFloor(req.getTargetFloor());
        }

        if (req.getSendPush() != null) {
            announcement.setSendPush(req.getSendPush());
        }
        if (req.getSendEmail() != null) {
            announcement.setSendEmail(req.getSendEmail());
        }
        if (req.getSendSms() != null) {
            announcement.setSendSms(req.getSendSms());
        }

        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement draft updated — id={}", saved.getId());
        return toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Publishes via an atomic compare-and-set on {@code publishedAt}, then creates
     * in-app notification rows for all scoped recipients inside the same transaction —
     * a publish either fully happened (visible in feed AND rows exist) or rolled back.
     * Already-published (or concurrently published) announcements yield 409 CONFLICT.
     * External push/email/SMS delivery remains stubbed (logged at INFO only).
     *
     * @throws AppException CONFLICT when the announcement is already published.
     */
    @Override
    @Transactional
    public AnnouncementResponse publishAnnouncement(UUID id, UUID principalId) {
        log.debug("publishAnnouncement — id={}", id);

        Announcement announcement = requireAnnouncement(id);

        // Single CAS guard: only the request that flips publishedAt NULL→now may dispatch.
        // Row-count 0 means already published or lost a concurrent race — both are 409.
        OffsetDateTime now = OffsetDateTime.now();
        int won = announcementRepository.publishIfDraft(id, now);
        if (won == 0) {
            throw new AppException(ErrorCode.CONFLICT, "Announcement already published.");
        }

        // The CAS update bypasses the persistence context — sync the managed entity
        // with the same timestamp instead of re-reading the row.
        announcement.setPublishedAt(now);

        dispatchInAppNotifications(announcement);

        // Stub: log external delivery intent. Push/email/SMS dispatch is a future sprint.
        log.info("Announcement {} published by {}. Delivery channels — push={}, email={}, sms={}.",
                announcement.getId(), principalId,
                announcement.isSendPush(), announcement.isSendEmail(), announcement.isSendSms());

        return toResponse(announcement);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks for an existing read record before inserting to maintain idempotency.
     * The DB unique constraint {@code uq_ar_unique} provides a safety net, but the
     * check avoids unnecessary insert attempts on repeated calls.
     */
    @Override
    @Transactional
    public MarkReadResponse markRead(UUID id, UUID principalId) {
        log.debug("markRead — announcementId={}, userId={}", id, principalId);

        Announcement announcement = requireAnnouncement(id);

        // SECURITY-FIX: SEC-07 — prevent marking draft announcements as read
        if (announcement.getPublishedAt() == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "Announcement not found.");
        }

        // Check whether the record already exists before attempting an insert.
        java.util.Optional<AnnouncementRead> existing =
                announcementReadRepository.findByAnnouncementIdAndUserId(id, principalId);
        if (existing.isPresent()) {
            return MarkReadResponse.builder()
                    .alreadyRead(true)
                    .readAt(existing.get().getReadAt())
                    .build();
        }

        User user = userRepository.findById(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "User not found: " + principalId));

        AnnouncementRead read = new AnnouncementRead();
        read.setAnnouncement(announcement);
        read.setUser(user);
        AnnouncementRead saved = announcementReadRepository.save(read);

        log.debug("markRead — new read record created for announcementId={}, userId={}", id, principalId);
        return MarkReadResponse.builder()
                .alreadyRead(false)
                .readAt(saved.getReadAt())
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Published announcements cannot be deleted to preserve resident read history.
     */
    @Override
    @Transactional
    public void deleteAnnouncement(UUID id) {
        log.debug("deleteAnnouncement — id={}", id);

        Announcement announcement = requireAnnouncement(id);

        // Prevent deletion of published announcements to preserve read history integrity.
        if (announcement.getPublishedAt() != null) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Cannot delete a published announcement.");
        }

        announcementRepository.delete(announcement);
        log.info("Announcement draft deleted — id={}", id);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Creates one in-app notification row per scoped recipient in a single batched insert.
     *
     * <p>Recipients come from {@code ResidentRepository.findRecipientUserIds} — the single
     * source of the ALL/BLOCK/FLOOR audience rule (mirrors the resident feed predicate,
     * guarded by {@code AnnouncementRecipientConsistencyTest}). User FKs are attached via
     * {@code getReferenceById} so no per-recipient SELECT is issued; {@code saveAll} flushes
     * as batched INSERTs ({@code hibernate.jdbc.batch_size}).
     *
     * @param announcement the just-published announcement (publishedAt already set).
     */
    private void dispatchInAppNotifications(Announcement announcement) {
        UUID targetBlockId = announcement.getTargetBlock() != null
                ? announcement.getTargetBlock().getId()
                : null;

        List<UUID> recipientIds = residentRepository.findRecipientUserIds(
                announcement.getScope(), targetBlockId, announcement.getTargetFloor());

        List<Notification> batch = new ArrayList<>(recipientIds.size());
        // Build the full batch in memory, then one saveAll — no logging or I/O in the loop.
        for (UUID userId : recipientIds) {
            Notification notification = new Notification();
            notification.setUser(userRepository.getReferenceById(userId));
            notification.setTitle(announcement.getTitle());
            notification.setBody("Có thông báo mới: " + announcement.getTitle());
            notification.setType(NotificationType.ANNOUNCEMENT_PUBLISHED);
            notification.setReferenceId(announcement.getId());
            notification.setReferenceType("Announcement");
            batch.add(notification);
        }
        notificationRepository.saveAll(batch);

        log.info("Announcement {} dispatched as in-app notifications to {} recipients.",
                announcement.getId(), batch.size());
    }

    /**
     * Loads an announcement by ID or throws NOT_FOUND.
     *
     * @param id the announcement UUID.
     * @return the loaded announcement entity.
     */
    private Announcement requireAnnouncement(UUID id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Announcement not found: " + id));
    }

    /**
     * Validates that the scope-targeting field combination is consistent.
     *
     * <p>Mirrors the DB-level CHECK constraint so errors are caught before the INSERT attempt.
     *
     * @param scope         the requested scope.
     * @param targetBlockId the target block UUID, may be null.
     * @param targetFloor   the target floor, may be null.
     */
    private void validateScopeConstraints(AnnouncementScope scope,
                                          UUID targetBlockId,
                                          Short targetFloor) {
        if (scope == AnnouncementScope.BLOCK && targetBlockId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "targetBlockId required for BLOCK scope.");
        }
        if (scope == AnnouncementScope.FLOOR
                && (targetBlockId == null || targetFloor == null)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "targetBlockId and targetFloor required for FLOOR scope.");
        }
    }

    /**
     * Returns the subset of the given page's announcement IDs that the user has read.
     *
     * <p>One query per page regardless of page size — the per-user {@code isRead}
     * flag is then resolved in memory by set membership.
     *
     * @param userId        the requesting user UUID.
     * @param announcements the page content.
     * @return set of announcement IDs read by the user; empty for an empty page.
     */
    private Set<UUID> readAnnouncementIds(UUID userId, List<Announcement> announcements) {
        // Guard the IN clause — Hibernate rejects an empty parameter list.
        if (announcements.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = announcements.stream().map(Announcement::getId).toList();
        return new HashSet<>(announcementReadRepository.findReadAnnouncementIds(userId, ids));
    }

    /**
     * Resolves creator display names for a page of announcements in a single batch query.
     *
     * <p>Collects the distinct non-null {@code createdBy} actor UUIDs and issues ONE
     * {@code findAllById} — never a per-row lookup — so list mapping stays free of N+1.
     *
     * @param announcements the announcements whose creator names are needed.
     * @return id&rarr;fullName map; empty when no announcement carries a creator UUID.
     */
    private Map<UUID, String> resolveCreatorNames(List<Announcement> announcements) {
        Set<UUID> ids = announcements.stream()
                .map(Announcement::getCreatedBy)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        // Guard the IN clause — and skip the round-trip when there is nothing to resolve.
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getFullName));
    }

    /**
     * Maps an {@link Announcement} entity to an {@link AnnouncementResponse} DTO
     * with {@code isRead = false}.
     *
     * <p>Used by mutation paths (create/update/publish) where no read record can
     * exist for the caller yet; read paths compute the real per-user flag via
     * {@link #toResponse(Announcement, boolean)}.
     *
     * @param announcement the entity to map.
     * @return the response DTO.
     */
    private AnnouncementResponse toResponse(Announcement announcement) {
        return toResponse(announcement, false);
    }

    /**
     * Maps an {@link Announcement} entity to an {@link AnnouncementResponse} DTO,
     * resolving the creator name with a single-entry batch lookup.
     *
     * <p>Single-row convenience used by mutation/detail paths. List paths must call
     * {@link #toResponse(Announcement, boolean, Map)} with a page-scoped name map so the
     * creator name is resolved in one query (no N+1).
     *
     * @param announcement the entity to map.
     * @param isRead       whether the requesting user has read this announcement.
     * @return the response DTO.
     */
    private AnnouncementResponse toResponse(Announcement announcement, boolean isRead) {
        return toResponse(announcement, isRead, resolveCreatorNames(List.of(announcement)));
    }

    /**
     * Maps an {@link Announcement} entity to an {@link AnnouncementResponse} DTO.
     *
     * <p>{@code createdBy} is now a plain actor UUID (Spring Data auditing). The creator's
     * display name is read from the pre-built {@code creatorNames} map, never a per-row query.
     *
     * @param announcement the entity to map.
     * @param isRead       whether the requesting user has read this announcement.
     * @param creatorNames id&rarr;fullName map covering the createdBy UUIDs being mapped.
     * @return the response DTO.
     */
    private AnnouncementResponse toResponse(Announcement announcement, boolean isRead,
                                            Map<UUID, String> creatorNames) {
        AnnouncementResponse.BlockRef blockRef = null;
        if (announcement.getTargetBlock() != null) {
            Block block = announcement.getTargetBlock();
            blockRef = AnnouncementResponse.BlockRef.builder()
                    .id(block.getId())
                    .name(block.getName())
                    .build();
        }

        AnnouncementResponse.UserRef creatorRef = null;
        UUID creatorId = announcement.getCreatedBy();
        if (creatorId != null) {
            creatorRef = AnnouncementResponse.UserRef.builder()
                    .id(creatorId)
                    .fullName(creatorNames.get(creatorId))
                    .build();
        }

        long readByCount = announcementReadRepository.countByAnnouncementId(announcement.getId());

        return AnnouncementResponse.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .type(announcement.getType())
                .targetScope(announcement.getScope())
                .targetBlock(blockRef)
                .targetFloor(announcement.getTargetFloor())
                .sendPush(announcement.isSendPush())
                .sendEmail(announcement.isSendEmail())
                .sendSms(announcement.isSendSms())
                .createdBy(creatorRef)
                .publishedAt(announcement.getPublishedAt())
                .createdAt(announcement.getCreatedAt())
                .readByCount(readByCount)
                .isRead(isRead)
                .build();
    }
}
