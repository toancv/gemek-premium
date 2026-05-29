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
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.OffsetDateTime;
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

    /**
     * Constructs the service with all required dependencies via constructor injection.
     *
     * @param announcementRepository     the announcement JPA repository.
     * @param announcementReadRepository the announcement read JPA repository.
     * @param blockRepository            the block JPA repository.
     * @param userRepository             the user JPA repository.
     * @param residentRepository         the resident JPA repository.
     */
    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository,
                                   AnnouncementReadRepository announcementReadRepository,
                                   BlockRepository blockRepository,
                                   UserRepository userRepository,
                                   ResidentRepository residentRepository) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.residentRepository = residentRepository;
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

            Page<AnnouncementResponse> page = announcementRepository
                    .findPublishedForApartment(blockId, floor, pageable)
                    .map(a -> toResponse(a));
            return PageResponse.of(page);
        }

        // ADMIN, TECHNICIAN, BOARD_MEMBER — all announcements.
        Page<AnnouncementResponse> page = announcementRepository.findAll(pageable)
                .map(a -> toResponse(a));
        return PageResponse.of(page);
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

        return toResponse(announcement);
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
        log.debug("createAnnouncement — type={}, scope={}", req.getType(), req.getScope());

        validateScopeConstraints(req.getScope(), req.getTargetBlockId(), req.getTargetFloor());

        User creator = userRepository.findById(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "User not found: " + principalId));

        Announcement announcement = new Announcement();
        announcement.setTitle(req.getTitle());
        announcement.setContent(req.getContent());
        announcement.setType(req.getType());
        announcement.setScope(req.getScope());
        announcement.setSendPush(req.getSendPush() != null ? req.getSendPush() : true);
        announcement.setSendEmail(req.getSendEmail() != null ? req.getSendEmail() : false);
        announcement.setSendSms(req.getSendSms() != null ? req.getSendSms() : false);
        announcement.setCreatedBy(creator);
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
            throw new AppException(ErrorCode.CONFLICT, "Cannot edit a published announcement.");
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
        AnnouncementScope newScope = req.getScope() != null ? req.getScope() : announcement.getScope();
        UUID newBlockId = req.getTargetBlockId() != null
                ? req.getTargetBlockId()
                : (announcement.getTargetBlock() != null ? announcement.getTargetBlock().getId() : null);
        Short newFloor = req.getTargetFloor() != null ? req.getTargetFloor() : announcement.getTargetFloor();

        validateScopeConstraints(newScope, newBlockId, newFloor);

        if (req.getScope() != null) {
            announcement.setScope(req.getScope());
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
     * <p>Sets {@code publishedAt} to now. Full notification dispatch is deferred to Module 10;
     * this method logs the intent at INFO level as a stub.
     */
    @Override
    @Transactional
    public AnnouncementResponse publishAnnouncement(UUID id, UUID principalId) {
        log.debug("publishAnnouncement — id={}", id);

        Announcement announcement = requireAnnouncement(id);

        // Idempotent: already published announcements are returned without error.
        if (announcement.getPublishedAt() != null) {
            log.debug("publishAnnouncement — id={} already published, returning as-is.", id);
            return toResponse(announcement);
        }

        announcement.setPublishedAt(OffsetDateTime.now());
        Announcement saved = announcementRepository.save(announcement);

        // Stub: log delivery intent. Full push/email/SMS dispatch wired in Module 10.
        log.info("Announcement {} published by {}. Delivery channels — push={}, email={}, sms={}.",
                saved.getId(), principalId,
                saved.isSendPush(), saved.isSendEmail(), saved.isSendSms());

        return toResponse(saved);
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
     * Maps an {@link Announcement} entity to an {@link AnnouncementResponse} DTO.
     *
     * @param announcement the entity to map.
     * @return the response DTO.
     */
    private AnnouncementResponse toResponse(Announcement announcement) {
        AnnouncementResponse.BlockRef blockRef = null;
        if (announcement.getTargetBlock() != null) {
            Block block = announcement.getTargetBlock();
            blockRef = AnnouncementResponse.BlockRef.builder()
                    .id(block.getId())
                    .name(block.getName())
                    .build();
        }

        AnnouncementResponse.UserRef creatorRef = null;
        if (announcement.getCreatedBy() != null) {
            User creator = announcement.getCreatedBy();
            creatorRef = AnnouncementResponse.UserRef.builder()
                    .id(creator.getId())
                    .fullName(creator.getFullName())
                    .build();
        }

        long readByCount = announcementReadRepository.countByAnnouncementId(announcement.getId());

        return AnnouncementResponse.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .type(announcement.getType())
                .scope(announcement.getScope())
                .targetBlock(blockRef)
                .targetFloor(announcement.getTargetFloor())
                .sendPush(announcement.isSendPush())
                .sendEmail(announcement.isSendEmail())
                .sendSms(announcement.isSendSms())
                .createdBy(creatorRef)
                .publishedAt(announcement.getPublishedAt())
                .createdAt(announcement.getCreatedAt())
                .readByCount(readByCount)
                .build();
    }
}
