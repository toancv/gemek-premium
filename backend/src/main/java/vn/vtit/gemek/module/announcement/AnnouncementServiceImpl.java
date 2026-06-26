/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

import jakarta.persistence.criteria.Predicate;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.storage.ContentDispositionUtil;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.common.storage.ObjectKeysObsoleteEvent;
import vn.vtit.gemek.module.announcement.dto.AnnouncementAttachmentResponse;
import vn.vtit.gemek.module.announcement.dto.AnnouncementMediaResponse;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.dto.MarkReadResponse;
import vn.vtit.gemek.module.announcement.dto.UpdateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementAttachment;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMedia;
import vn.vtit.gemek.module.announcement.entity.AnnouncementMediaKind;
import vn.vtit.gemek.module.announcement.entity.AnnouncementRead;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.repository.AnnouncementAttachmentRepository;
import vn.vtit.gemek.module.announcement.repository.AnnouncementMediaRepository;
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

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    /**
     * Maximum allowed length of an announcement Markdown body. A single broadcast body never needs
     * more than this; the bound is a cheap guard against TEXT-column abuse, not a product limit.
     */
    private static final int MAX_CONTENT_LENGTH = 20_000;

    /**
     * Detects a raw HTML tag (opening or closing) to reject HTML in a Markdown body. Deliberately does
     * NOT match Markdown autolinks ({@code <https://…>} — a scheme's ':' follows the name) nor email
     * autolinks ({@code <a@b.com>} — '@' follows) nor a bare '<' in prose ("a < b").
     *
     * <p>Known limitation (accepted for this lightweight guard): an HTML tag written literally inside
     * a Markdown code span / fenced block (e.g. {@code `<div>`}) is also rejected — this cheap guard
     * has no code-fence awareness. Embedding raw tags in announcement code blocks is not a real use case.
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9]*[\\s/>]");

    /** Maximum image rows per announcement (C2.2 cap, server-enforced in-tx). */
    private static final int MAX_MEDIA_PER_ANNOUNCEMENT = 5;

    /** Maximum total media bytes per announcement (50 MB, C2.2 cap, server-enforced in-tx). */
    private static final long MAX_MEDIA_TOTAL_BYTES = 50L * 1024 * 1024;

    /** Allowed media content types, validated by Tika on the bytes (not the client header/extension). */
    private static final Set<String> ALLOWED_MEDIA_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    /** Tika instance for magic-byte content-type detection (thread-safe). */
    private static final Tika TIKA = new Tika();

    /** Maximum attachment rows per announcement (C3 cap, INDEPENDENT of the image cap, in-tx). */
    private static final int MAX_ATTACHMENTS_PER_ANNOUNCEMENT = 5;

    /** Maximum total attachment bytes per announcement (50 MB, C3 cap, INDEPENDENT of images, in-tx). */
    private static final long MAX_ATTACHMENT_TOTAL_BYTES = 50L * 1024 * 1024;

    /**
     * Maximum bytes for a single attachment (10 MB). Belt-and-suspenders with the servlet
     * {@code spring.servlet.multipart.max-file-size=10MB} so a direct service call (no servlet) is
     * still bounded and yields a coded error rather than relying on the 413 the servlet emits.
     */
    private static final long MAX_ATTACHMENT_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * Allowed attachment content types (C3), validated by Tika on the bytes — pdf/docx/xlsx/pptx/txt
     * ONLY. Renderable/script-capable types (html, svg) and csv are EXCLUDED. Macro/JS risk in Office
     * docs is mitigated by forced-download (the browser never executes them).
     */
    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String MIME_TXT = "text/plain";

    private static final Set<String> ALLOWED_ATTACHMENT_MIME_TYPES =
            Set.of(MIME_PDF, MIME_DOCX, MIME_XLSX, MIME_PPTX, MIME_TXT);

    /** Forced-download content type override applied to every attachment presigned URL. */
    private static final String DOWNLOAD_RESPONSE_CONTENT_TYPE = "application/octet-stream";

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ResidentRepository residentRepository;
    private final NotificationRepository notificationRepository;
    private final AnnouncementMediaRepository mediaRepository;
    private final AnnouncementAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructs the service with all required dependencies via constructor injection.
     *
     * @param announcementRepository     the announcement JPA repository.
     * @param announcementReadRepository the announcement read JPA repository.
     * @param blockRepository            the block JPA repository.
     * @param userRepository             the user JPA repository.
     * @param residentRepository         the resident JPA repository.
     * @param notificationRepository     the notification JPA repository for publish dispatch.
     * @param mediaRepository            the announcement media JPA repository (C2.2).
     * @param attachmentRepository       the announcement attachment JPA repository (C3).
     * @param fileStorageService         the MinIO storage service for media upload/delete (C2.2).
     * @param eventPublisher             publisher for the after-commit object-cleanup event (C2.2).
     */
    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository,
                                   AnnouncementReadRepository announcementReadRepository,
                                   BlockRepository blockRepository,
                                   UserRepository userRepository,
                                   ResidentRepository residentRepository,
                                   NotificationRepository notificationRepository,
                                   AnnouncementMediaRepository mediaRepository,
                                   AnnouncementAttachmentRepository attachmentRepository,
                                   FileStorageService fileStorageService,
                                   ApplicationEventPublisher eventPublisher) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.residentRepository = residentRepository;
        this.notificationRepository = notificationRepository;
        this.mediaRepository = mediaRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
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
            // Multi-residency: union the published feed across ALL the caller's active apartments,
            // each announcement at most once (DISTINCT). One batch query for the residencies (N+1-safe).
            List<Resident> residencies = residentRepository.findAllActiveByUserId(principalId);
            if (residencies.isEmpty()) {
                // No active residency — return empty page rather than an error.
                return PageResponse.of(Page.empty(pageable));
            }

            Page<Announcement> residentPage = announcementRepository
                    .findAll(publishedForResidenciesSpec(residencies), pageable);
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
     * Builds the resident-feed visibility {@link Specification} unioned across ALL of a caller's
     * active residencies, returning each matching announcement AT MOST ONCE.
     *
     * <p>An announcement is visible when its scope is ALL, or BLOCK matching one of the caller's
     * active blocks, or FLOOR matching one of the caller's active (block, floor) PAIRS. The FLOOR
     * predicate is built per-pair on purpose: a naive {@code targetFloor IN floors AND targetBlock IN
     * blocks} would cross-match (a caller on BlockA/floor3 + BlockB/floor5 would wrongly see a FLOOR
     * announcement for BlockA/floor5). {@code query.distinct(true)} collapses the row to a single
     * announcement even when several residencies match it (e.g. an ALL announcement), so a
     * building-wide announcement appears exactly once regardless of residency count.
     *
     * <p>The visibility predicate is the per-apartment mirror of
     * {@code AnnouncementRepository.findPublishedForApartment} (and thus of the recipient-dispatch
     * query) — the union here does not change the single-apartment rule, preserving feed↔dispatch
     * consistency ({@code AnnouncementRecipientConsistencyTest}).
     *
     * @param residencies the caller's active residencies; must be non-empty.
     * @return a specification selecting the distinct published announcements visible to the caller.
     */
    private Specification<Announcement> publishedForResidenciesSpec(List<Resident> residencies) {
        Set<UUID> blockIds = residencies.stream()
                .map(r -> r.getApartment().getBlock().getId())
                .collect(Collectors.toSet());
        return (root, query, cb) -> {
            // DISTINCT-by-announcement-id — each announcement at most once across all residencies.
            query.distinct(true);
            List<Predicate> visibility = new ArrayList<>();
            // ALL scope — visible to every resident.
            visibility.add(cb.equal(root.get("scope"), AnnouncementScope.ALL));
            // BLOCK scope — targetBlock is one of the caller's active blocks.
            visibility.add(cb.and(
                    cb.equal(root.get("scope"), AnnouncementScope.BLOCK),
                    root.get("targetBlock").get("id").in(blockIds)));
            // FLOOR scope — match per (block, floor) pair to avoid cross-apartment floor leakage.
            for (Resident r : residencies) {
                Apartment apt = r.getApartment();
                visibility.add(cb.and(
                        cb.equal(root.get("scope"), AnnouncementScope.FLOOR),
                        cb.equal(root.get("targetBlock").get("id"), apt.getBlock().getId()),
                        cb.equal(root.get("targetFloor"), apt.getFloor())));
            }
            return cb.and(
                    cb.isNotNull(root.get("publishedAt")),
                    cb.or(visibility.toArray(new Predicate[0])));
        };
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
        // Detail-only: attach a media manifest with FRESH presigned URLs, gated by the C2.1 scope
        // check so an out-of-scope resident gets the text but no media URLs (no leak).
        List<AnnouncementResponse.MediaRef> manifest = buildMediaManifest(announcement, principalId, role);
        // Detail-only: attach the downloadable-attachment manifest, gated by the SAME C2.1 scope check.
        List<AnnouncementResponse.AttachmentRef> attachments =
                buildAttachmentManifest(announcement, principalId, role);
        return toResponse(announcement, isRead, resolveCreatorNames(List.of(announcement)),
                manifest, attachments);
    }

    /**
     * Builds the detail ATTACHMENT manifest: each row mapped to a FRESH short-lived FORCED-DOWNLOAD
     * presigned URL ({@code Content-Disposition: attachment} + {@code application/octet-stream}, both
     * signed), minted only when the caller may access the announcement. Access reuses the SAME C2.1
     * presign gate ({@link #assertMediaPresignAccess}) as the image manifest — checked ONCE because every
     * row of one announcement shares the same access decision. An out-of-scope resident (or any denied
     * role) gets an empty list, never a leaked URL.
     *
     * @param announcement the announcement being detailed.
     * @param principalId  the caller's UUID.
     * @param role         the caller's role string.
     * @return the attachment manifest (possibly empty), never null.
     */
    private List<AnnouncementResponse.AttachmentRef> buildAttachmentManifest(Announcement announcement,
                                                                             UUID principalId, String role) {
        List<AnnouncementAttachment> rows =
                attachmentRepository.findByAnnouncementIdOrderByCreatedAtAsc(announcement.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        // Gate once via the C2.1 presign access rule; denial → empty manifest (no leak), not a 500.
        try {
            assertMediaPresignAccess(rows.get(0).getObjectKey(), principalId, role);
        } catch (AppException denied) {
            return List.of();
        }
        return rows.stream()
                .map(a -> AnnouncementResponse.AttachmentRef.builder()
                        .id(a.getId())
                        .displayFilename(a.getDisplayFilename())
                        .sizeBytes(a.getSizeBytes())
                        .downloadUrl(fileStorageService.presign(a.getObjectKey(),
                                ContentDispositionUtil.attachment(a.getDisplayFilename()),
                                DOWNLOAD_RESPONSE_CONTENT_TYPE))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Builds the detail media manifest for an announcement: each row mapped to a FRESH presigned GET
     * URL, minted only when the caller may access the media. Access reuses the C2.1 presign gate
     * ({@link #assertMediaPresignAccess}) verbatim — checked ONCE because every row of one announcement
     * shares the same access decision (same announcement id). An out-of-scope resident (or any denied
     * role) gets an empty manifest, never a leaked URL; a published announcement with no media is empty.
     *
     * @param announcement the announcement being detailed.
     * @param principalId  the caller's UUID.
     * @param role         the caller's role string.
     * @return the manifest (possibly empty), never null.
     */
    private List<AnnouncementResponse.MediaRef> buildMediaManifest(Announcement announcement,
                                                                   UUID principalId, String role) {
        List<AnnouncementMedia> rows =
                mediaRepository.findByAnnouncementIdOrderByCreatedAtAsc(announcement.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        // Gate once via the C2.1 presign access rule; denial → empty manifest (no leak), not a 500.
        try {
            assertMediaPresignAccess(rows.get(0).getObjectKey(), principalId, role);
        } catch (AppException denied) {
            return List.of();
        }
        return rows.stream()
                .map(m -> AnnouncementResponse.MediaRef.builder()
                        .id(m.getId())
                        .kind(m.getKind())
                        .url(fileStorageService.presign(m.getObjectKey()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assertMediaPresignAccess(String objectKey, UUID principalId, String role) {
        // Parse the owning announcement id FIRST — a malformed key is denied for every role
        // (FORBIDDEN, never a 500 from a bad UUID reaching MinIO).
        UUID announcementId = parseAnnouncementIdFromKey(objectKey);
        if (announcementId == null) {
            log.warn("Denied announcement-media presign — malformed key.");
            throw new AppException(ErrorCode.FORBIDDEN, "Access denied to announcement media.");
        }

        // ADMIN / BOARD_MEMBER are unrestricted, mirroring announcement read (drafts included —
        // they author/preview draft media). Direct analogue of enforcePhotoAccess's staff bypass.
        if ("ADMIN".equals(role) || "BOARD_MEMBER".equals(role)) {
            return;
        }

        // Only RESIDENT may reach a scope check; every other role (e.g. TECHNICIAN) is denied —
        // announcements are not a technician surface.
        if (!"RESIDENT".equals(role)) {
            log.warn("Denied announcement-media presign — role {} has no announcement audience.", role);
            throw new AppException(ErrorCode.FORBIDDEN, "Access denied to announcement media.");
        }

        // Scope mirror: the single exists query also enforces published-only (draft media is
        // never resident-visible) and nonexistent-id → false (deny). No 500 path.
        if (!announcementRepository.existsReadableByResident(announcementId, principalId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Access denied to announcement media.");
        }
    }

    /**
     * Recovers the announcement UUID from a media object key per {@link AnnouncementService#MEDIA_KEY_PREFIX}.
     *
     * <p>Convention: {@code announcements/{announcementId}/{uuid-filename}} — the id is the first path
     * segment after the prefix. Returns {@code null} (caller denies) for any key that lacks a segment or
     * whose first segment is not a UUID, so a malformed key never produces a 500.
     *
     * @param objectKey the MinIO object key (already known to start with the media prefix).
     * @return the parsed announcement UUID, or {@code null} if the key is malformed.
     */
    private static UUID parseAnnouncementIdFromKey(String objectKey) {
        if (objectKey == null || !objectKey.startsWith(AnnouncementService.MEDIA_KEY_PREFIX)) {
            return null;
        }
        String remainder = objectKey.substring(AnnouncementService.MEDIA_KEY_PREFIX.length());
        int slash = remainder.indexOf('/');
        // Require a non-empty id segment followed by a filename ("<id>/<file>") — reject "announcements/x".
        if (slash <= 0 || slash == remainder.length() - 1) {
            return null;
        }
        try {
            return UUID.fromString(remainder.substring(0, slash));
        } catch (IllegalArgumentException ex) {
            // First segment is not a UUID — malformed key, deny.
            return null;
        }
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
        validateContent(req.getContent());

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

        // Target block/floor are DERIVED from scope (parity with updateAnnouncement): a stray block/floor
        // sent alongside a less-specific scope is NOT persisted, so a created row can never contradict its
        // scope. Required-presence is already enforced by validateScopeConstraints above.
        AnnouncementScope scope = req.getTargetScope();
        if (scope != AnnouncementScope.ALL && req.getTargetBlockId() != null) {
            Block block = blockRepository.findById(req.getTargetBlockId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                            "Block not found: " + req.getTargetBlockId()));
            announcement.setTargetBlock(block);
        }
        if (scope == AnnouncementScope.FLOOR && req.getTargetFloor() != null) {
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
            validateContent(req.getContent());
            announcement.setContent(req.getContent());
        }
        if (req.getType() != null) {
            announcement.setType(req.getType());
        }

        // Target block/floor are DERIVED from scope, not partial-update-preserved: scope is the
        // source of truth. A downgrade (e.g. BLOCK->ALL or FLOOR->BLOCK) must CLEAR the now-irrelevant
        // target columns even if the client omitted them (sends null) or sent stale values — otherwise
        // a null-means-unchanged binding would strand a block/floor that contradicts the new scope.
        AnnouncementScope newScope = req.getTargetScope() != null ? req.getTargetScope() : announcement.getScope();
        // Effective candidates: prefer the incoming value, else keep the existing — then normalize by scope.
        UUID effectiveBlockId = req.getTargetBlockId() != null
                ? req.getTargetBlockId()
                : (announcement.getTargetBlock() != null ? announcement.getTargetBlock().getId() : null);
        Short effectiveFloor = req.getTargetFloor() != null ? req.getTargetFloor() : announcement.getTargetFloor();

        // Normalize the target fields to exactly what the scope uses (clear the rest).
        switch (newScope) {
            case ALL:
                // ALL targets everyone — no block, no floor.
                effectiveBlockId = null;
                effectiveFloor = null;
                break;
            case BLOCK:
                // BLOCK uses the block only — floor is irrelevant.
                effectiveFloor = null;
                break;
            case FLOOR:
                // FLOOR uses both block and floor — keep both (validated below).
                break;
            default:
                break;
        }

        // Parity with create: block required for BLOCK/FLOOR, floor required for FLOOR.
        validateScopeConstraints(newScope, effectiveBlockId, effectiveFloor);

        announcement.setScope(newScope);
        // Set block/floor authoritatively — including clearing them on a downgrade.
        if (effectiveBlockId == null) {
            // ALL scope (or no block) → clear.
            announcement.setTargetBlock(null);
        } else if (req.getTargetBlockId() != null) {
            // Client supplied a (possibly changed) block → load + validate it.
            final UUID blockIdToLoad = req.getTargetBlockId();
            Block block = blockRepository.findById(blockIdToLoad)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                            "Block not found: " + blockIdToLoad));
            announcement.setTargetBlock(block);
        }
        // else: BLOCK/FLOOR keeping the existing block — reuse the already-attached managed entity, NO
        // re-fetch, so a content-only or floor-only edit never 404s if that block row was later deleted.
        announcement.setTargetFloor(effectiveFloor);

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

        // Collect media AND attachment object keys BEFORE the delete; the rows go via FK ON DELETE
        // CASCADE, but the MinIO objects must be cleaned up separately — schedule both surfaces for
        // best-effort after-commit delete so neither images nor documents are orphaned in storage.
        List<String> objectKeys = new ArrayList<>();
        mediaRepository.findByAnnouncementIdOrderByCreatedAtAsc(id).stream()
                .map(AnnouncementMedia::getObjectKey)
                .forEach(objectKeys::add);
        attachmentRepository.findByAnnouncementIdOrderByCreatedAtAsc(id).stream()
                .map(AnnouncementAttachment::getObjectKey)
                .forEach(objectKeys::add);

        announcementRepository.delete(announcement);
        scheduleObjectCleanup(objectKeys);
        log.info("Announcement draft deleted — id={}, objects={}", id, objectKeys.size());
    }

    // =========================================================================
    // Media (C2.2) — ADMIN, drafts only
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public AnnouncementMediaResponse uploadMedia(UUID announcementId, MultipartFile file,
                                                 String kind, UUID principalId) {
        log.debug("uploadMedia — announcementId={}, kind={}", announcementId, kind);

        Announcement announcement = requireDraftForMedia(announcementId);
        AnnouncementMediaKind mediaKind = parseKind(kind);

        // Magic-byte validation: trust the bytes, never the filename extension or client Content-Type.
        String detectedMime = detectImageMime(file);

        long fileSize = file.getSize();

        // Cover-replace: a second cover REPLACES the first — account for the freed slot/bytes BEFORE
        // the cap check so replacing an existing cover never trips the count/size limits.
        Optional<AnnouncementMedia> replacedCover = mediaKind == AnnouncementMediaKind.COVER
                ? mediaRepository.findByAnnouncementIdAndKind(announcementId, AnnouncementMediaKind.COVER)
                : Optional.empty();
        long replacedCount = replacedCover.isPresent() ? 1 : 0;
        long replacedBytes = replacedCover.map(m -> m.getSizeBytes() != null ? m.getSizeBytes() : 0L)
                .orElse(0L);

        long effectiveCount = mediaRepository.countByAnnouncementId(announcementId) - replacedCount;
        long effectiveBytes = mediaRepository.sumSizeBytesByAnnouncementId(announcementId) - replacedBytes;

        if (effectiveCount + 1 > MAX_MEDIA_PER_ANNOUNCEMENT) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED,
                    "Tối đa " + MAX_MEDIA_PER_ANNOUNCEMENT + " ảnh mỗi thông báo.");
        }
        if (effectiveBytes + fileSize > MAX_MEDIA_TOTAL_BYTES) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED,
                    "Tổng dung lượng ảnh của thông báo vượt quá 50MB.");
        }

        // Remove the old cover row in-tx and schedule its object for after-commit delete.
        replacedCover.ifPresent(old -> {
            mediaRepository.delete(old);
            mediaRepository.flush();
            scheduleObjectCleanup(List.of(old.getObjectKey()));
        });

        // Key per C2.1 convention announcements/{announcementId}/{uuid}{ext} — id is the first segment.
        String objectKey = AnnouncementService.MEDIA_KEY_PREFIX + announcementId + "/"
                + UUID.randomUUID() + extensionFor(detectedMime);

        try {
            fileStorageService.upload(objectKey, file.getInputStream(), detectedMime, fileSize);
        } catch (IOException e) {
            log.error("Failed to read announcement media upload stream: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể đọc tệp tải lên.");
        }

        AnnouncementMedia media = new AnnouncementMedia();
        media.setAnnouncement(announcement);
        media.setObjectKey(objectKey);
        media.setContentType(detectedMime);
        media.setSizeBytes(fileSize);
        media.setKind(mediaKind);
        media.setOriginalFilename(file.getOriginalFilename());
        AnnouncementMedia saved = mediaRepository.save(media);

        log.info("Announcement {} media uploaded — kind={}, size={}B, key={}",
                announcementId, mediaKind, fileSize, objectKey);
        return toMediaResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AnnouncementMediaResponse> listMedia(UUID announcementId) {
        log.debug("listMedia — announcementId={}", announcementId);

        // Existence check so a missing announcement is a clean 404 rather than an empty list.
        requireAnnouncement(announcementId);
        return mediaRepository.findByAnnouncementIdOrderByCreatedAtAsc(announcementId).stream()
                .map(this::toMediaResponse)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMedia(UUID announcementId, UUID mediaId) {
        log.debug("deleteMedia — announcementId={}, mediaId={}", announcementId, mediaId);

        requireDraftForMedia(announcementId);
        // Dual-key lookup: the row must belong to the announcement in the path (no cross-announcement delete).
        AnnouncementMedia media = mediaRepository.findByAnnouncementIdAndId(announcementId, mediaId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Announcement media not found: " + mediaId));

        String objectKey = media.getObjectKey();
        mediaRepository.delete(media);
        scheduleObjectCleanup(List.of(objectKey));
        log.info("Announcement {} media deleted — mediaId={}", announcementId, mediaId);
    }

    // =========================================================================
    // Attachments (C3) — ADMIN, drafts only, downloadable documents
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public AnnouncementAttachmentResponse uploadAttachment(UUID announcementId, MultipartFile file,
                                                           UUID principalId) {
        log.debug("uploadAttachment — announcementId={}", announcementId);

        Announcement announcement = requireDraftForAttachment(announcementId);

        long fileSize = file.getSize();
        // Per-file cap FIRST (cheap) — coded error rather than relying on the servlet 413.
        if (fileSize > MAX_ATTACHMENT_FILE_BYTES) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_ATTACHMENT_TOO_LARGE,
                    "Tệp đính kèm vượt quá 10MB.");
        }

        // Magic-byte validation: trust the bytes (pdf/docx/xlsx/pptx/txt), never the filename/header.
        String detectedMime = detectAttachmentMime(file);

        // Caps are INDEPENDENT of the image caps — counted over announcement_attachment only.
        long currentCount = attachmentRepository.countByAnnouncementId(announcementId);
        long currentBytes = attachmentRepository.sumSizeBytesByAnnouncementId(announcementId);
        if (currentCount + 1 > MAX_ATTACHMENTS_PER_ANNOUNCEMENT) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED,
                    "Tối đa " + MAX_ATTACHMENTS_PER_ANNOUNCEMENT + " tệp đính kèm mỗi thông báo.");
        }
        if (currentBytes + fileSize > MAX_ATTACHMENT_TOTAL_BYTES) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED,
                    "Tổng dung lượng tệp đính kèm của thông báo vượt quá 50MB.");
        }

        // Key per C2.1 convention announcements/{announcementId}/{uuid}{ext} — id is the first segment,
        // so the EXISTING presign scope gate parses it unchanged.
        String objectKey = AnnouncementService.MEDIA_KEY_PREFIX + announcementId + "/"
                + UUID.randomUUID() + attachmentExtensionFor(detectedMime);

        try {
            fileStorageService.upload(objectKey, file.getInputStream(), detectedMime, fileSize);
        } catch (IOException e) {
            log.error("Failed to read announcement attachment upload stream: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể đọc tệp tải lên.");
        }

        AnnouncementAttachment attachment = new AnnouncementAttachment();
        attachment.setAnnouncement(announcement);
        attachment.setObjectKey(objectKey);
        attachment.setContentType(detectedMime);
        attachment.setSizeBytes(fileSize);
        attachment.setDisplayFilename(sanitizeDisplayFilename(file.getOriginalFilename()));
        AnnouncementAttachment saved = attachmentRepository.save(attachment);

        log.info("Announcement {} attachment uploaded — type={}, size={}B, key={}",
                announcementId, detectedMime, fileSize, objectKey);
        return toAttachmentResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AnnouncementAttachmentResponse> listAttachments(UUID announcementId) {
        log.debug("listAttachments — announcementId={}", announcementId);

        // Existence check so a missing announcement is a clean 404 rather than an empty list.
        requireAnnouncement(announcementId);
        return attachmentRepository.findByAnnouncementIdOrderByCreatedAtAsc(announcementId).stream()
                .map(this::toAttachmentResponse)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteAttachment(UUID announcementId, UUID attachmentId) {
        log.debug("deleteAttachment — announcementId={}, attachmentId={}", announcementId, attachmentId);

        requireDraftForAttachment(announcementId);
        // Dual-key lookup: the row must belong to the announcement in the path (no cross-announcement delete).
        AnnouncementAttachment attachment = attachmentRepository
                .findByAnnouncementIdAndId(announcementId, attachmentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Announcement attachment not found: " + attachmentId));

        String objectKey = attachment.getObjectKey();
        attachmentRepository.delete(attachment);
        scheduleObjectCleanup(List.of(objectKey));
        log.info("Announcement {} attachment deleted — attachmentId={}", announcementId, attachmentId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Loads an announcement and asserts it is a DRAFT (media is mutable on drafts only).
     *
     * @param announcementId the announcement id.
     * @return the draft announcement.
     * @throws AppException NOT_FOUND if missing, ANNOUNCEMENT_NOT_DRAFT if already published.
     */
    private Announcement requireDraftForMedia(UUID announcementId) {
        Announcement announcement = requireAnnouncement(announcementId);
        if (announcement.getPublishedAt() != null) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_NOT_DRAFT,
                    "Không thể chỉnh sửa ảnh của thông báo đã xuất bản.");
        }
        return announcement;
    }

    /**
     * Parses the request kind string into the enum, case-insensitively.
     *
     * @param kind the raw {@code kind} request param.
     * @return the parsed {@link AnnouncementMediaKind}.
     * @throws AppException VALIDATION_ERROR if null/blank/unrecognised.
     */
    private AnnouncementMediaKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu loại ảnh (kind).");
        }
        try {
            return AnnouncementMediaKind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Loại ảnh không hợp lệ: phải là cover hoặc inline.");
        }
    }

    /**
     * Detects the real image content type from the file bytes via Tika and asserts it is allowed.
     *
     * @param file the uploaded multipart file.
     * @return the detected, allowed MIME type (the value stored as content_type).
     * @throws AppException ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED if not jpg/png/webp; INTERNAL_ERROR on read failure.
     */
    private String detectImageMime(MultipartFile file) {
        try {
            String detected = TIKA.detect(file.getInputStream());
            if (!ALLOWED_MEDIA_MIME_TYPES.contains(detected)) {
                throw new AppException(ErrorCode.ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED,
                        "Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP.");
            }
            return detected;
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể kiểm tra định dạng tệp.");
        }
    }

    /**
     * Maps an allowed image MIME type to its canonical file extension.
     *
     * @param mime the detected MIME type (already validated as allowed).
     * @return the extension including the dot, e.g. {@code ".jpg"}.
     */
    private String extensionFor(String mime) {
        return switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    /**
     * Publishes an after-commit MinIO object-cleanup event for the given keys (no-op if empty).
     *
     * @param objectKeys the object keys to delete after the current transaction commits.
     */
    private void scheduleObjectCleanup(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new ObjectKeysObsoleteEvent(objectKeys));
    }

    /**
     * Loads an announcement and asserts it is a DRAFT (attachments are mutable on drafts only).
     *
     * @param announcementId the announcement id.
     * @return the draft announcement.
     * @throws AppException NOT_FOUND if missing, ANNOUNCEMENT_NOT_DRAFT if already published.
     */
    private Announcement requireDraftForAttachment(UUID announcementId) {
        Announcement announcement = requireAnnouncement(announcementId);
        if (announcement.getPublishedAt() != null) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_NOT_DRAFT,
                    "Không thể chỉnh sửa tệp đính kèm của thông báo đã xuất bản.");
        }
        return announcement;
    }

    /**
     * Detects the real document content type from the file bytes and asserts it is an allowed attachment
     * type (pdf/docx/xlsx/pptx/txt). OOXML files (docx/xlsx/pptx) are ZIP containers that tika-core
     * reports as {@code application/zip}; they are disambiguated by inspecting the zip's internal part
     * layout ({@link #classifyZipContainer}) — still pure content inspection, never the filename. A bare
     * zip, html, svg, csv, or anything else falls outside the allow-list and is rejected.
     *
     * @param file the uploaded multipart file.
     * @return the detected, allowed document MIME type (the value stored as content_type).
     * @throws AppException ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED if not allowed; INTERNAL_ERROR on read failure.
     */
    private String detectAttachmentMime(MultipartFile file) {
        try {
            String detected = TIKA.detect(file.getInputStream());
            // tika-core can't peek inside a zip — resolve OOXML subtypes from the container layout.
            if ("application/zip".equals(detected) || "application/x-tika-ooxml".equals(detected)) {
                detected = classifyZipContainer(file);
            }
            if (!ALLOWED_ATTACHMENT_MIME_TYPES.contains(detected)) {
                throw new AppException(ErrorCode.ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED,
                        "Chỉ chấp nhận tệp PDF, DOCX, XLSX, PPTX hoặc TXT.");
            }
            return detected;
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể kiểm tra định dạng tệp.");
        }
    }

    /**
     * Classifies a ZIP container as an OOXML document type by inspecting its entry names. Real Office
     * files carry a {@code word/}, {@code xl/}, or {@code ppt/} part tree; the first match wins. Any zip
     * lacking these (a plain archive) returns {@code application/zip} so the caller rejects it.
     *
     * @param file the uploaded multipart file (a detected zip container).
     * @return the OOXML MIME type, or {@code application/zip} if it is not an Office document.
     * @throws IOException if the stream cannot be read.
     */
    private String classifyZipContainer(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream();
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            // Scan part names; OOXML always has a top-level word/ | xl/ | ppt/ directory.
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/")) {
                    return MIME_DOCX;
                }
                if (name.startsWith("xl/")) {
                    return MIME_XLSX;
                }
                if (name.startsWith("ppt/")) {
                    return MIME_PPTX;
                }
            }
        }
        // No OOXML part tree → a plain zip, which is not an allowed attachment type.
        return "application/zip";
    }

    /**
     * Maps an allowed document MIME type to its canonical file extension (object-key cosmetic only —
     * serving forces a download with the stored display filename regardless of this).
     *
     * @param mime the detected MIME type (already validated as allowed).
     * @return the extension including the dot, e.g. {@code ".pdf"}.
     */
    private String attachmentExtensionFor(String mime) {
        return switch (mime) {
            case MIME_PDF -> ".pdf";
            case MIME_DOCX -> ".docx";
            case MIME_XLSX -> ".xlsx";
            case MIME_PPTX -> ".pptx";
            case MIME_TXT -> ".txt";
            default -> "";
        };
    }

    /**
     * Sanitizes the client filename for STORAGE/display: trims, drops path separators and control chars,
     * and bounds the length to the column width. The full RFC 6266 header sanitization (against
     * header/query-param injection) happens at presign time via {@code ContentDispositionUtil}.
     *
     * @param original the client-supplied original filename, may be null.
     * @return a non-blank display filename safe to persist (≤255 chars).
     */
    private String sanitizeDisplayFilename(String original) {
        if (original == null || original.isBlank()) {
            return "tep-dinh-kem";
        }
        StringBuilder sb = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            // Drop path separators and control chars; keep the rest (incl. Vietnamese) for display.
            if (c == '/' || c == '\\' || c < 0x20 || c == 0x7F) {
                continue;
            }
            sb.append(c);
        }
        String cleaned = sb.toString().trim();
        if (cleaned.isEmpty()) {
            return "tep-dinh-kem";
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }

    /**
     * Maps an attachment entity to its response DTO.
     *
     * @param attachment the attachment entity.
     * @return the response DTO.
     */
    private AnnouncementAttachmentResponse toAttachmentResponse(AnnouncementAttachment attachment) {
        return AnnouncementAttachmentResponse.builder()
                .id(attachment.getId())
                .displayFilename(attachment.getDisplayFilename())
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSizeBytes())
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    /**
     * Maps a media entity to its response DTO.
     *
     * @param media the media entity.
     * @return the response DTO.
     */
    private AnnouncementMediaResponse toMediaResponse(AnnouncementMedia media) {
        return AnnouncementMediaResponse.builder()
                .id(media.getId())
                .kind(media.getKind())
                .contentType(media.getContentType())
                .sizeBytes(media.getSizeBytes())
                .originalFilename(media.getOriginalFilename())
                .objectKey(media.getObjectKey())
                .createdAt(media.getCreatedAt())
                .build();
    }

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
     * Lightweight secondary guard on the Markdown body. The frontend safe renderer is the primary XSS
     * defense; this rejects two cheap classes of bad input on write: over-length bodies and raw HTML
     * tags (the stored format is Markdown, not HTML).
     *
     * @param content the Markdown body to validate; may be null (treated as no-op).
     */
    private void validateContent(String content) {
        if (content == null) {
            return;
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_CONTENT_TOO_LONG,
                    "Announcement content exceeds the maximum length of " + MAX_CONTENT_LENGTH + " characters.");
        }
        if (HTML_TAG_PATTERN.matcher(content).find()) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED,
                    "Announcement content must be Markdown and may not contain raw HTML tags.");
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
        // List/mutation paths carry no media manifest — only the detail path mints presigned URLs.
        return toResponse(announcement, isRead, creatorNames, List.of());
    }

    /**
     * Maps an {@link Announcement} entity to an {@link AnnouncementResponse} DTO, including the media
     * manifest. Only the detail path passes a non-empty {@code media} list (presigned per request);
     * all other callers pass an empty list.
     *
     * @param announcement the entity to map.
     * @param isRead       whether the requesting user has read this announcement.
     * @param creatorNames id&rarr;fullName map covering the createdBy UUIDs being mapped.
     * @param media        the media manifest (empty for list/mutation responses).
     * @return the response DTO.
     */
    private AnnouncementResponse toResponse(Announcement announcement, boolean isRead,
                                            Map<UUID, String> creatorNames,
                                            List<AnnouncementResponse.MediaRef> media) {
        // List/mutation paths carry no attachment manifest — only the detail path mints download URLs.
        return toResponse(announcement, isRead, creatorNames, media, List.of());
    }

    /**
     * Maps an {@link Announcement} entity to an {@link AnnouncementResponse} DTO, including both the
     * media manifest and the attachment manifest. Only the detail path passes non-empty manifests
     * (presigned per request); all other callers pass empty lists.
     *
     * @param announcement the entity to map.
     * @param isRead       whether the requesting user has read this announcement.
     * @param creatorNames id&rarr;fullName map covering the createdBy UUIDs being mapped.
     * @param media        the media manifest (empty for list/mutation responses).
     * @param attachments  the attachment manifest (empty for list/mutation responses).
     * @return the response DTO.
     */
    private AnnouncementResponse toResponse(Announcement announcement, boolean isRead,
                                            Map<UUID, String> creatorNames,
                                            List<AnnouncementResponse.MediaRef> media,
                                            List<AnnouncementResponse.AttachmentRef> attachments) {
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
                .media(media)
                .attachments(attachments)
                .build();
    }
}
