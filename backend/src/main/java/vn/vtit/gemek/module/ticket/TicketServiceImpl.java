/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;

import jakarta.persistence.criteria.Predicate;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.contractor.entity.Contractor;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.dto.AssignTicketRequest;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.SlaReportResponse;
import vn.vtit.gemek.module.ticket.dto.TicketDetailResponse;
import vn.vtit.gemek.module.ticket.dto.TicketSummaryResponse;
import vn.vtit.gemek.module.ticket.dto.UpdateTicketStatusRequest;
import vn.vtit.gemek.module.ticket.entity.PhotoPhase;
import vn.vtit.gemek.module.ticket.entity.Ticket;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPhoto;
import vn.vtit.gemek.module.ticket.entity.TicketPriority;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;
import vn.vtit.gemek.module.ticket.entity.TicketStatusHistory;
import vn.vtit.gemek.module.ticket.repository.TicketPhotoRepository;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;
import vn.vtit.gemek.module.ticket.repository.TicketStatusHistoryRepository;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.notification.SubscriptionService;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.entity.SubscriptionJoinedVia;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link TicketService}.
 *
 * <p>All write operations are individually transactional. Read operations run
 * under a class-level {@code readOnly} transaction to skip dirty-checking overhead.
 */
@Service
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    /** Reference-type label for ticket notifications and thread subscriptions. */
    private static final String TICKET_REFERENCE_TYPE = "Ticket";

    /** MinIO key prefix for ticket photos — matches the upload key generator. */
    private static final String TICKET_KEY_PREFIX = "tickets/";

    /** MinIO key prefix for announcement images (N2) — public-read surface per E3. */
    private static final String ANNOUNCEMENT_KEY_PREFIX = "announcements/";

    /** List-visibility filter value: caller's own household tickets only (default). */
    private static final String VISIBILITY_MINE = "mine";

    /** List-visibility filter value: public (community) tickets only. */
    private static final String VISIBILITY_COMMUNITY = "community";

    /** Placeholder shown instead of the submitter's name on redacted public views (G8). */
    private static final String REDACTED_SUBMITTER_LABEL = "Cư dân";

    /** Maximum number of files allowed per upload request. */
    private static final int MAX_FILES_PER_UPLOAD = 5;

    /** Maximum allowed file size in bytes (10 MB). */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    // SECURITY-FIX: Tika instance for magic-byte MIME detection, replacing client-supplied Content-Type
    private static final Tika TIKA = new Tika();
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png");

    /**
     * SLA hours per category. SUGGESTION_FEEDBACK is absent intentionally — it carries no SLA.
     */
    private static final Map<TicketCategory, Integer> SLA_HOURS;

    static {
        // EnumMap for O(1) lookup.
        SLA_HOURS = new EnumMap<>(TicketCategory.class);
        SLA_HOURS.put(TicketCategory.MAINTENANCE_REPAIR, 24);
        SLA_HOURS.put(TicketCategory.COMPLAINT, 48);
        SLA_HOURS.put(TicketCategory.ADMINISTRATIVE, 72);
        SLA_HOURS.put(TicketCategory.OTHER, 72);
    }

    /**
     * Valid status transitions. Terminal states map to empty sets.
     */
    private static final Map<TicketStatus, Set<TicketStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(TicketStatus.class);
        VALID_TRANSITIONS.put(TicketStatus.NEW,
                EnumSet.of(TicketStatus.ASSIGNED, TicketStatus.CANCELLED));
        VALID_TRANSITIONS.put(TicketStatus.ASSIGNED,
                EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED, TicketStatus.NEW));
        VALID_TRANSITIONS.put(TicketStatus.IN_PROGRESS,
                EnumSet.of(TicketStatus.DONE, TicketStatus.CANCELLED));
        VALID_TRANSITIONS.put(TicketStatus.DONE, EnumSet.noneOf(TicketStatus.class));
        VALID_TRANSITIONS.put(TicketStatus.CANCELLED, EnumSet.noneOf(TicketStatus.class));
    }

    private final TicketRepository ticketRepository;
    private final TicketPhotoRepository photoRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final ApartmentRepository apartmentRepository;
    private final UserRepository userRepository;
    private final ResidentRepository residentRepository;
    private final ContractorRepository contractorRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Constructs the service with all required dependencies via explicit constructor injection.
     *
     * @param ticketRepository       the ticket JPA repository.
     * @param photoRepository        the ticket photo JPA repository.
     * @param historyRepository      the ticket status history JPA repository.
     * @param apartmentRepository    the apartment JPA repository.
     * @param userRepository         the user JPA repository.
     * @param residentRepository     the resident JPA repository.
     * @param contractorRepository   the contractor JPA repository.
     * @param fileStorageService     the MinIO file storage service.
     * @param notificationService    the notification service for single-recipient alerts.
     * @param notificationRepository the notification JPA repository for batched dispatch.
     * @param subscriptionService    the notification-thread membership service (N3).
     */
    public TicketServiceImpl(TicketRepository ticketRepository,
                             TicketPhotoRepository photoRepository,
                             TicketStatusHistoryRepository historyRepository,
                             ApartmentRepository apartmentRepository,
                             UserRepository userRepository,
                             ResidentRepository residentRepository,
                             ContractorRepository contractorRepository,
                             FileStorageService fileStorageService,
                             NotificationService notificationService,
                             NotificationRepository notificationRepository,
                             SubscriptionService subscriptionService) {
        this.ticketRepository = ticketRepository;
        this.photoRepository = photoRepository;
        this.historyRepository = historyRepository;
        this.apartmentRepository = apartmentRepository;
        this.userRepository = userRepository;
        this.residentRepository = residentRepository;
        this.contractorRepository = contractorRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.subscriptionService = subscriptionService;
    }

    // =========================================================================
    // Read operations
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<TicketSummaryResponse> listTickets(UUID principalId, String role,
                                                           String visibility,
                                                           List<TicketStatus> statuses,
                                                           TicketCategory category,
                                                           TicketPriority priority,
                                                           UUID apartmentId,
                                                           Pageable pageable) {
        log.debug("listTickets — role={}, visibility={}, statuses={}, category={}",
                role, visibility, statuses, category);

        // Reject unknown visibility values early; null defaults to "mine" semantics.
        if (visibility != null && !VISIBILITY_MINE.equals(visibility)
                && !VISIBILITY_COMMUNITY.equals(visibility)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "visibility must be 'mine' or 'community'.");
        }

        Specification<Ticket> spec = buildScopeSpec(principalId, role, visibility)
                .and(buildFilterSpec(statuses, category, priority, apartmentId));

        // RESIDENT rows outside the caller's own household can only be public tickets;
        // their summaries are redacted (G8) so the list cannot leak what the detail hides.
        if ("RESIDENT".equals(role)) {
            UUID myApartmentId = residentRepository.findActiveByUserId(principalId)
                    .map(resident -> resident.getApartment().getId())
                    .orElse(null);
            Page<TicketSummaryResponse> page = ticketRepository.findAll(spec, pageable)
                    .map(ticket -> ticket.getApartment().getId().equals(myApartmentId)
                            ? toSummary(ticket) : toRedactedSummary(ticket));
            return PageResponse.of(page);
        }

        Page<TicketSummaryResponse> page = ticketRepository.findAll(spec, pageable)
                .map(this::toSummary);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketDetailResponse getTicketDetail(UUID id, UUID principalId, String role) {
        log.debug("getTicketDetail — id={}, role={}", id, role);

        Ticket ticket = requireTicket(id);
        enforceReadAccess(ticket, principalId, role);

        // G8: a resident outside the ticket's household can only be here because the
        // ticket is public — they get the redacted view, never the full mapping.
        TicketDetailResponse response;
        if ("RESIDENT".equals(role) && !isHouseholdMember(ticket, principalId)) {
            response = toRedactedDetail(ticket);
        } else {
            // SEC-03/SEC-08: strip submitter phone for roles without PII entitlement.
            boolean includePhone = !"TECHNICIAN".equals(role) && !"BOARD_MEMBER".equals(role);
            response = toDetail(ticket, includePhone);
        }

        // N3 P7 viewer flag: only RESIDENT callers have follow semantics; staff
        // views and mutation responses leave the flag null.
        if ("RESIDENT".equals(role)) {
            response.setIsFollowing(subscriptionService
                    .isFollower(principalId, TICKET_REFERENCE_TYPE, ticket.getId()));
        }
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlaReportResponse getSlaReport(LocalDate from, LocalDate to, TicketCategory category) {
        log.debug("getSlaReport — from={}, to={}, category={}", from, to, category);

        OffsetDateTime fromDt = (from != null) ? from.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()) : null;
        OffsetDateTime toDt = (to != null) ? to.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()) : null;
        String categoryStr = (category != null) ? category.name() : null;

        List<Object[]> rows = ticketRepository.getSlaReportByCategory(fromDt, toDt, categoryStr);
        List<SlaReportResponse.CategoryStats> byCategory = new ArrayList<>();

        long totalSum = 0;
        long completedSum = 0;
        long slaBreachedSum = 0;
        double weightedResolutionHours = 0;
        int resolutionCount = 0;
        double weightedRating = 0;
        int ratingCount = 0;

        for (Object[] row : rows) {
            // Index mapping: 0=category, 1=total, 2=completed, 3=slaBreached, 4=avgResolutionHours, 5=avgRating
            String catName = (String) row[0];
            long total = ((Number) row[1]).longValue();
            long completed = ((Number) row[2]).longValue();
            long slaBreached = ((Number) row[3]).longValue();
            Double avgResHours = row[4] != null ? ((Number) row[4]).doubleValue() : null;
            Double avgRat = row[5] != null ? ((Number) row[5]).doubleValue() : null;

            double breachRate = (total > 0) ? (double) slaBreached / total : 0.0;

            byCategory.add(SlaReportResponse.CategoryStats.builder()
                    .category(TicketCategory.valueOf(catName))
                    .total(total)
                    .completed(completed)
                    .slaBreached(slaBreached)
                    .slaBreachRate(breachRate)
                    .avgResolutionHours(avgResHours)
                    .avgRating(avgRat)
                    .build());

            totalSum += total;
            completedSum += completed;
            slaBreachedSum += slaBreached;

            // Accumulate weighted averages for summary row.
            if (avgResHours != null && completed > 0) {
                weightedResolutionHours += avgResHours * completed;
                resolutionCount += completed;
            }
            if (avgRat != null) {
                // Use total as weight for rating (any status can contribute a rating after DONE).
                weightedRating += avgRat * total;
                ratingCount += total;
            }
        }

        Double summaryAvgHours = (resolutionCount > 0) ? weightedResolutionHours / resolutionCount : null;
        Double summaryAvgRating = (ratingCount > 0) ? weightedRating / ratingCount : null;
        double summaryBreachRate = (totalSum > 0) ? (double) slaBreachedSum / totalSum : 0.0;

        SlaReportResponse.SummaryStats summary = SlaReportResponse.SummaryStats.builder()
                .total(totalSum)
                .completed(completedSum)
                .slaBreached(slaBreachedSum)
                .slaBreachRate(summaryBreachRate)
                .avgResolutionHours(summaryAvgHours)
                .avgRating(summaryAvgRating)
                .build();

        return SlaReportResponse.builder()
                .period(SlaReportResponse.PeriodRef.builder().from(from).to(to).build())
                .summary(summary)
                .byCategory(byCategory)
                .build();
    }

    // =========================================================================
    // Write operations
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TicketDetailResponse createTicket(CreateTicketRequest req, UUID principalId, String role) {
        log.debug("createTicket — category={}, role={}", req.getCategory(), role);

        Apartment apartment = apartmentRepository.findById(req.getApartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Apartment not found: " + req.getApartmentId()));

        // RESIDENT callers may only submit for their active apartment.
        if ("RESIDENT".equals(role)) {
            Resident activeResident = residentRepository.findActiveByUserId(principalId)
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                            "No active resident record for this user."));
            if (!activeResident.getApartment().getId().equals(apartment.getId())) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Residents may only submit tickets for their own apartment.");
            }
        }

        User submitter = userRepository.findById(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "User not found: " + principalId));

        OffsetDateTime now = OffsetDateTime.now();

        Ticket ticket = new Ticket();
        ticket.setApartment(apartment);
        ticket.setSubmittedBy(submitter);
        ticket.setCategory(req.getCategory());
        ticket.setTitle(req.getTitle());
        ticket.setDescription(req.getDescription());
        ticket.setStatus(TicketStatus.NEW);
        ticket.setPriority(req.getPriority() != null ? req.getPriority() : TicketPriority.MEDIUM);
        // G3: community visibility is chosen once at creation and immutable afterwards.
        ticket.setPublic(Boolean.TRUE.equals(req.getIsPublic()));

        // Compute SLA deadline from category; SUGGESTION_FEEDBACK has no SLA.
        Integer slaHours = SLA_HOURS.get(req.getCategory());
        if (slaHours != null) {
            ticket.setSlaDeadline(now.plusHours(slaHours));
        }

        Ticket saved = ticketRepository.save(ticket);

        // Initial history entry: null → NEW.
        appendHistory(saved, null, TicketStatus.NEW, submitter, null);

        // N3: creator joins the notification thread.
        subscriptionService.subscribe(principalId, TICKET_REFERENCE_TYPE, saved.getId(),
                SubscriptionJoinedVia.CREATOR);

        // C1: notify active admins, excluding the actor (an admin creating a ticket
        // must not self-notify).
        List<UUID> adminIds = userRepository.findActiveUserIdsByRole(UserRole.ADMIN).stream()
                .filter(adminId -> !adminId.equals(principalId))
                .toList();
        dispatchTicketNotifications(adminIds,
                "Phản ánh mới",
                "Phản ánh mới: \"" + saved.getTitle() + "\" — căn hộ " + apartment.getUnitNumber()
                        + ", tòa " + apartment.getBlock().getName() + ".",
                NotificationType.TICKET_CREATED,
                saved.getId());

        log.info("Ticket created — id={}, category={}", saved.getId(), saved.getCategory());
        return toDetail(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TicketDetailResponse assignTicket(UUID id, AssignTicketRequest req, UUID principalId) {
        log.debug("assignTicket — ticketId={}", id);

        Ticket ticket = requireTicket(id);

        // Both assignees set simultaneously is a client error.
        if (req.getAssignedToUserId() != null && req.getAssignedToContractorId() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Provide either assignedToUserId or assignedToContractorId, not both.");
        }

        // Contractor assignment is only valid for MAINTENANCE_REPAIR.
        if (req.getAssignedToContractorId() != null
                && ticket.getCategory() != TicketCategory.MAINTENANCE_REPAIR) {
            throw new AppException(ErrorCode.CONTRACTOR_ASSIGNMENT_NOT_ALLOWED,
                    "Contractors may only be assigned to MAINTENANCE_REPAIR tickets.");
        }

        User caller = userRepository.findById(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "User not found: " + principalId));

        // Clear previous assignees before setting new ones.
        ticket.setAssignedToUser(null);
        ticket.setAssignedToContractor(null);

        if (req.getAssignedToUserId() != null) {
            User staff = userRepository.findById(req.getAssignedToUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                            "Staff user not found: " + req.getAssignedToUserId()));
            ticket.setAssignedToUser(staff);
        }

        if (req.getAssignedToContractorId() != null) {
            Contractor contractor = contractorRepository.findById(req.getAssignedToContractorId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                            "Contractor not found: " + req.getAssignedToContractorId()));
            ticket.setAssignedToContractor(contractor);
        }

        if (req.getScheduledDate() != null) {
            ticket.setScheduledDate(req.getScheduledDate());
        }

        // Automatically transition NEW → ASSIGNED when an assignee is set.
        TicketStatus oldStatus = ticket.getStatus();
        if (oldStatus == TicketStatus.NEW
                && (ticket.getAssignedToUser() != null || ticket.getAssignedToContractor() != null)) {
            ticket.setStatus(TicketStatus.ASSIGNED);
            appendHistory(ticket, oldStatus, TicketStatus.ASSIGNED, caller, req.getNotes());
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket assigned — id={}, status={}", saved.getId(), saved.getStatus());

        // Thread snapshot BEFORE the assignee joins — C3 goes to the existing
        // participants; the new assignee receives C2 instead, never both.
        List<UUID> threadBeforeAssign = subscriptionService
                .participantUserIds(TICKET_REFERENCE_TYPE, saved.getId());

        // Notify the assigned technician (C2) and add them to the thread.
        // The old assignee's subscription row is intentionally kept on reassign (G4).
        if (saved.getAssignedToUser() != null) {
            UUID assigneeId = saved.getAssignedToUser().getId();
            subscriptionService.subscribe(assigneeId, TICKET_REFERENCE_TYPE, saved.getId(),
                    SubscriptionJoinedVia.ASSIGNEE);
            notificationService.createNotification(
                    assigneeId,
                    "Phản ánh được phân công",
                    "Phản ánh \"" + saved.getTitle() + "\" đã được phân công cho bạn.",
                    NotificationType.TICKET_ASSIGNED,
                    saved.getId(),
                    TICKET_REFERENCE_TYPE);
        }

        // C3: the NEW → ASSIGNED auto-transition tells the thread the ticket was
        // accepted for processing. Excludes the actor and the just-notified assignee.
        if (oldStatus == TicketStatus.NEW && saved.getStatus() == TicketStatus.ASSIGNED) {
            UUID assigneeId = saved.getAssignedToUser() != null
                    ? saved.getAssignedToUser().getId() : null;
            List<UUID> recipients = threadBeforeAssign.stream()
                    .filter(participantId -> !participantId.equals(principalId)
                            && !participantId.equals(assigneeId))
                    .toList();
            dispatchTicketNotifications(recipients,
                    "Cập nhật phản ánh",
                    "Phản ánh \"" + saved.getTitle() + "\" đã được tiếp nhận và phân công xử lý.",
                    NotificationType.TICKET_STATUS_CHANGED,
                    saved.getId());
        }

        return toDetail(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TicketDetailResponse updateStatus(UUID id, UpdateTicketStatusRequest req,
                                             UUID principalId, String role) {
        log.debug("updateStatus — ticketId={}, targetStatus={}, role={}", id, req.getStatus(), role);

        Ticket ticket = requireTicket(id);

        // TECHNICIAN may only update tickets assigned to them.
        if ("TECHNICIAN".equals(role)) {
            boolean isAssigned = ticket.getAssignedToUser() != null
                    && ticket.getAssignedToUser().getId().equals(principalId);
            if (!isAssigned) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Technicians may only update status on tickets assigned to them.");
            }
        }

        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus targetStatus = req.getStatus();

        // Guard against illegal transitions.
        Set<TicketStatus> allowed = VALID_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + currentStatus + " to " + targetStatus + ".");
        }

        User caller = userRepository.findById(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "User not found: " + principalId));

        // Set completedDate when marking as DONE.
        if (targetStatus == TicketStatus.DONE) {
            ticket.setCompletedDate(OffsetDateTime.now());
        }

        if (req.getResolutionNotes() != null) {
            ticket.setResolutionNotes(req.getResolutionNotes());
        }

        ticket.setStatus(targetStatus);
        appendHistory(ticket, currentStatus, targetStatus, caller, req.getNotes());

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket status updated — id={}, {} → {}", saved.getId(), currentStatus, targetStatus);

        // C4: tell the thread about the transition, excluding the actor.
        List<UUID> threadRecipients = subscriptionService
                .participantUserIds(TICKET_REFERENCE_TYPE, saved.getId()).stream()
                .filter(participantId -> !participantId.equals(principalId))
                .toList();
        dispatchTicketNotifications(threadRecipients,
                "Cập nhật phản ánh",
                "Phản ánh \"" + saved.getTitle() + "\" chuyển sang trạng thái: "
                        + TicketStatusLabels.labelOf(targetStatus) + ".",
                NotificationType.TICKET_STATUS_CHANGED,
                saved.getId());

        // C5: completion additionally prompts the submitter to rate (G7 dedicated type).
        UUID submitterId = saved.getSubmittedBy().getId();
        if (targetStatus == TicketStatus.DONE && !submitterId.equals(principalId)) {
            notificationService.createNotification(
                    submitterId,
                    "Đánh giá xử lý phản ánh",
                    "Phản ánh \"" + saved.getTitle() + "\" đã hoàn tất. Vui lòng đánh giá chất lượng xử lý.",
                    NotificationType.TICKET_RATING_REQUESTED,
                    saved.getId(),
                    TICKET_REFERENCE_TYPE);
        }

        return toDetail(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<TicketDetailResponse.PhotoResponse> uploadPhotos(UUID id, List<MultipartFile> files,
                                                                  PhotoPhase phase,
                                                                  UUID principalId, String role) {
        log.debug("uploadPhotos — ticketId={}, phase={}, fileCount={}", id, phase, files.size());

        Ticket ticket = requireTicket(id);

        // RESIDENT callers: verify own apartment; phase must be BEFORE.
        if ("RESIDENT".equals(role)) {
            Resident activeResident = residentRepository.findActiveByUserId(principalId)
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                            "No active resident record for this user."));
            if (!activeResident.getApartment().getId().equals(ticket.getApartment().getId())) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Residents may only upload photos for their own apartment's tickets.");
            }
            if (phase != PhotoPhase.BEFORE) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Residents may only upload BEFORE photos.");
            }
        }

        if (files.size() > MAX_FILES_PER_UPLOAD) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Maximum " + MAX_FILES_PER_UPLOAD + " files per upload.");
        }

        // Validate each file before uploading any.
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "File " + file.getOriginalFilename() + " exceeds the 10 MB size limit.");
            }
            // SECURITY-FIX: use magic-byte detection instead of trusting client-supplied Content-Type
            validateFileMimeType(file);
        }

        User uploader = userRepository.findById(principalId).orElse(null);

        List<TicketDetailResponse.PhotoResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            String ext = resolveExtension(file.getContentType());
            String objectKey = "tickets/" + id + "/" + phase.name().toLowerCase()
                    + "/" + UUID.randomUUID() + ext;

            try {
                fileStorageService.upload(objectKey, file.getInputStream(),
                        file.getContentType(), file.getSize());
            } catch (java.io.IOException e) {
                log.error("Failed to read upload stream for {}: {}", originalName, e.getMessage(), e);
                throw new AppException(ErrorCode.INTERNAL_ERROR, "Could not read uploaded file.");
            }

            TicketPhoto photo = new TicketPhoto();
            photo.setTicket(ticket);
            photo.setFileUrl(objectKey);
            photo.setFileName(originalName);
            photo.setMimeType(file.getContentType());
            photo.setFileSize((int) file.getSize());
            photo.setPhase(phase);
            photo.setUploadedBy(uploader);

            TicketPhoto saved = photoRepository.save(photo);

            responses.add(TicketDetailResponse.PhotoResponse.builder()
                    .id(saved.getId())
                    .phase(saved.getPhase())
                    .presignedUrl(fileStorageService.presign(saved.getFileUrl()))
                    .fileName(saved.getFileName())
                    .mimeType(saved.getMimeType())
                    .fileSizeBytes(saved.getFileSize())
                    .uploadedAt(saved.getUploadedAt())
                    .build());
        }

        log.info("Uploaded {} photos for ticket {}", responses.size(), id);
        return responses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deletePhoto(UUID ticketId, UUID photoId) {
        log.debug("deletePhoto — ticketId={}, photoId={}", ticketId, photoId);

        // Verify the ticket exists.
        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Ticket not found: " + ticketId);
        }

        TicketPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Photo not found: " + photoId));

        // Confirm the photo belongs to the requested ticket.
        if (!photo.getTicket().getId().equals(ticketId)) {
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Photo " + photoId + " does not belong to ticket " + ticketId + ".");
        }

        String objectKey = photo.getFileUrl();
        photoRepository.deleteByTicketIdAndId(ticketId, photoId);
        // Delete from MinIO after DB record is gone; failure is logged but non-fatal.
        fileStorageService.delete(objectKey);

        log.info("Photo deleted — photoId={}, ticketId={}", photoId, ticketId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TicketDetailResponse rateTicket(UUID id, RateTicketRequest req, UUID principalId) {
        log.debug("rateTicket — ticketId={}, rating={}", id, req.getRating());

        Ticket ticket = requireTicket(id);

        // Only the submitter may rate.
        if (!ticket.getSubmittedBy().getId().equals(principalId)) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Only the ticket submitter may submit a rating.");
        }

        // Ticket must be DONE before rating.
        if (ticket.getStatus() != TicketStatus.DONE) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION, "Ticket must be DONE to rate.");
        }

        // Rating may only be submitted once.
        if (ticket.getRating() != null) {
            throw new AppException(ErrorCode.TICKET_ALREADY_RATED, "Ticket rating has already been submitted.");
        }

        ticket.setRating(req.getRating().shortValue());
        ticket.setRatingComment(req.getComment());

        Ticket saved = ticketRepository.save(ticket);

        // Recalculate the contractor's average rating if one was assigned.
        if (saved.getAssignedToContractor() != null) {
            contractorRepository.recalculateRating(saved.getAssignedToContractor().getId());
            log.debug("Contractor rating recalculated — contractorId={}",
                    saved.getAssignedToContractor().getId());
        }

        // C6: tell the assigned staff member about the rating (actor is the submitter;
        // the self-check is defensive for a self-assigned edge case).
        if (saved.getAssignedToUser() != null
                && !saved.getAssignedToUser().getId().equals(principalId)) {
            notificationService.createNotification(
                    saved.getAssignedToUser().getId(),
                    "Phản ánh được đánh giá",
                    "Phản ánh \"" + saved.getTitle() + "\" được cư dân đánh giá "
                            + saved.getRating() + "/5 sao.",
                    NotificationType.TICKET_RATED,
                    saved.getId(),
                    TICKET_REFERENCE_TYPE);
        }

        log.info("Ticket rated — id={}, rating={}", saved.getId(), saved.getRating());
        return toDetail(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void followTicket(UUID id, UUID principalId) {
        log.debug("followTicket — ticketId={}", id);

        Ticket ticket = requireVisibleToResident(id, principalId);
        // Joining the thread is all that is needed — P3 dispatch reads participantUserIds.
        subscriptionService.subscribe(principalId, TICKET_REFERENCE_TYPE, ticket.getId(),
                SubscriptionJoinedVia.FOLLOWER);
        log.info("Ticket {} — follower subscribed.", ticket.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void unfollowTicket(UUID id, UUID principalId) {
        log.debug("unfollowTicket — ticketId={}", id);

        Ticket ticket = requireVisibleToResident(id, principalId);
        subscriptionService.unsubscribe(principalId, TICKET_REFERENCE_TYPE, ticket.getId());
        log.info("Ticket {} — follower unsubscribed.", ticket.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assertPresignAccess(String fileUrl, UUID principalId, String role) {
        // H2: prefix-routed per-surface rules (API-SPEC §13 access matrix, ruling E3).
        // Explicit dispatch: known prefix → its rule; anything else → deny-by-default.
        if (fileUrl != null && fileUrl.startsWith(TICKET_KEY_PREFIX)) {
            TicketPhoto photo = photoRepository.findByFileUrl(fileUrl)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                            "No photo record found for the requested key."));
            // F-05 gate: photos keep the strict household/staff rule. Deliberately NOT
            // delegating to enforceReadAccess — its public-ticket bypass must never
            // widen the presign surface (G8).
            enforcePhotoAccess(photo.getTicket(), principalId, role);
            return;
        }
        if (fileUrl != null && fileUrl.startsWith(ANNOUNCEMENT_KEY_PREFIX)) {
            // E3: announcements are broadcast content — any authenticated user may
            // presign. Intentionally no DB-row requirement yet: keys are random UUIDs
            // and a nonexistent key simply 404s at MinIO; N2 adds a row check when
            // its attachment table exists.
            return;
        }
        throw new AppException(ErrorCode.FORBIDDEN, "Unknown file surface.");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Loads a ticket by ID or throws NOT_FOUND.
     *
     * @param id the ticket UUID.
     * @return the loaded ticket entity.
     */
    private Ticket requireTicket(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Ticket not found: " + id));
    }

    /**
     * Loads a ticket for a resident's follow/unfollow request, hiding invisible tickets.
     *
     * <p>A private ticket outside the caller's household throws the same NOT_FOUND as a
     * missing ID — a resident must not be able to probe whether a ticket exists.
     *
     * @param id          the ticket UUID.
     * @param principalId the calling resident's UUID.
     * @return the visible ticket entity.
     */
    private Ticket requireVisibleToResident(UUID id, UUID principalId) {
        Ticket ticket = requireTicket(id);
        // Visible = public, or the caller's own household.
        if (!ticket.isPublic() && !isHouseholdMember(ticket, principalId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Ticket not found: " + id);
        }
        return ticket;
    }

    /**
     * Enforces read access rules based on the caller's role.
     *
     * <p>RESIDENT may only view tickets for their active apartment.
     * TECHNICIAN may only view tickets assigned to them.
     * ADMIN and BOARD_MEMBER have unrestricted read access.
     *
     * @param ticket      the ticket to check access for.
     * @param principalId the caller's UUID.
     * @param role        the caller's role string.
     */
    private void enforceReadAccess(Ticket ticket, UUID principalId, String role) {
        if ("RESIDENT".equals(role)) {
            // Public tickets are readable by every resident (redacted upstream, G8).
            if (ticket.isPublic()) {
                return;
            }
            Resident activeResident = residentRepository.findActiveByUserId(principalId)
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                            "No active resident record for this user."));
            if (!activeResident.getApartment().getId().equals(ticket.getApartment().getId())) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Access denied to this ticket.");
            }
        } else if ("TECHNICIAN".equals(role)) {
            boolean isAssigned = ticket.getAssignedToUser() != null
                    && ticket.getAssignedToUser().getId().equals(principalId);
            boolean isNew = ticket.getStatus() == TicketStatus.NEW;
            if (!isAssigned && !isNew) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Technicians may only view tickets assigned to them or with NEW status.");
            }
        }
        // ADMIN and BOARD_MEMBER: no restriction.
    }

    /**
     * Enforces photo (presign) access with the strict pre-P5 rule: RESIDENT must
     * belong to the ticket's household — a public flag grants NO photo access.
     *
     * <p>Kept separate from {@link #enforceReadAccess} on purpose: photos can show
     * the inside of a home, and the presign surface must not widen before F-05
     * (IDOR hardening) lands. Do not merge these two methods.
     *
     * @param ticket      the ticket owning the photo.
     * @param principalId the caller's UUID.
     * @param role        the caller's role string.
     */
    private void enforcePhotoAccess(Ticket ticket, UUID principalId, String role) {
        if ("RESIDENT".equals(role)) {
            Resident activeResident = residentRepository.findActiveByUserId(principalId)
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                            "No active resident record for this user."));
            if (!activeResident.getApartment().getId().equals(ticket.getApartment().getId())) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Access denied to this ticket.");
            }
        } else if ("TECHNICIAN".equals(role)) {
            boolean isAssigned = ticket.getAssignedToUser() != null
                    && ticket.getAssignedToUser().getId().equals(principalId);
            boolean isNew = ticket.getStatus() == TicketStatus.NEW;
            if (!isAssigned && !isNew) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Technicians may only view tickets assigned to them or with NEW status.");
            }
        }
        // ADMIN and BOARD_MEMBER: no restriction.
    }

    /**
     * Returns whether the caller is an active resident of the ticket's apartment.
     *
     * @param ticket      the ticket to check.
     * @param principalId the caller's UUID.
     * @return {@code true} when the caller's active residency matches the ticket's apartment.
     */
    private boolean isHouseholdMember(Ticket ticket, UUID principalId) {
        return residentRepository.findActiveByUserId(principalId)
                .map(resident -> resident.getApartment().getId()
                        .equals(ticket.getApartment().getId()))
                .orElse(false);
    }

    /**
     * Builds a role-scoped {@link Specification} that restricts the visible ticket set.
     *
     * <ul>
     *   <li>ADMIN / BOARD_MEMBER — no scope restriction; {@code visibility} ignored.</li>
     *   <li>TECHNICIAN — assigned to principal OR status is NEW; {@code visibility} ignored.</li>
     *   <li>RESIDENT — {@code null}/"mine": apartment matches the principal's active
     *       apartment (pre-P5 behavior, keeps the existing FE unchanged);
     *       "community": public tickets only.</li>
     * </ul>
     *
     * @param principalId the caller's UUID.
     * @param role        the caller's role string.
     * @param visibility  optional list filter ("mine" | "community"); validated by the caller.
     * @return the scope specification.
     */
    private Specification<Ticket> buildScopeSpec(UUID principalId, String role, String visibility) {
        if ("TECHNICIAN".equals(role)) {
            return (root, query, cb) -> cb.or(
                    cb.equal(root.get("assignedToUser").get("id"), principalId),
                    cb.equal(root.get("status"), TicketStatus.NEW)
            );
        }
        if ("RESIDENT".equals(role)) {
            // Community tab: every public ticket, regardless of household.
            if (VISIBILITY_COMMUNITY.equals(visibility)) {
                return (root, query, cb) -> cb.isTrue(root.get("isPublic"));
            }
            // Default ("mine"): own household only — identical to pre-P5 scoping.
            return (root, query, cb) -> {
                java.util.Optional<Resident> residentOpt =
                        residentRepository.findActiveByUserId(principalId);
                if (residentOpt.isEmpty()) {
                    // No active residency — return zero rows.
                    return cb.disjunction();
                }
                UUID apartmentId = residentOpt.get().getApartment().getId();
                return cb.equal(root.get("apartment").get("id"), apartmentId);
            };
        }
        // ADMIN, BOARD_MEMBER — no restriction.
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Builds a filter {@link Specification} from optional request parameters.
     *
     * @param statuses    optional status filter; null or empty list matches all statuses.
     * @param category    optional category filter.
     * @param priority    optional priority filter.
     * @param apartmentId optional apartment filter.
     * @return the composed filter specification.
     */
    private Specification<Ticket> buildFilterSpec(List<TicketStatus> statuses, TicketCategory category,
                                                   TicketPriority priority, UUID apartmentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (apartmentId != null) {
                predicates.add(cb.equal(root.get("apartment").get("id"), apartmentId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Appends a single {@link TicketStatusHistory} record for the given transition.
     *
     * @param ticket    the ticket whose status changed.
     * @param oldStatus the status before the transition; {@code null} for the initial NEW entry.
     * @param newStatus the status after the transition.
     * @param changedBy the user who triggered the transition; may be {@code null}.
     * @param notes     optional notes.
     */
    private void appendHistory(Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus,
                               User changedBy, String notes) {
        TicketStatusHistory history = new TicketStatusHistory();
        history.setTicket(ticket);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setNotes(notes);
        historyRepository.save(history);
        log.debug("Status history appended — ticketId={}, {} → {}", ticket.getId(), oldStatus, newStatus);
    }

    /**
     * Creates one in-app notification row per recipient in a single batched insert.
     *
     * <p>Same pattern as {@code AnnouncementServiceImpl.dispatchInAppNotifications}:
     * user FKs attached via {@code getReferenceById} (no per-recipient SELECT),
     * one {@code saveAll} flushing as batched INSERTs, no logging or I/O in the loop.
     * Runs inside the calling mutation transaction — dispatch is atomic with the
     * ticket change. Empty recipient lists are a no-op.
     *
     * @param userIds  recipient user IDs (actor exclusion already applied by callers).
     * @param title    VN notification title.
     * @param body     VN notification body.
     * @param type     the notification type.
     * @param ticketId the ticket UUID used as the reference.
     */
    private void dispatchTicketNotifications(List<UUID> userIds, String title, String body,
                                             NotificationType type, UUID ticketId) {
        if (userIds.isEmpty()) {
            return;
        }
        List<Notification> batch = new ArrayList<>(userIds.size());
        // Build the full batch in memory, then one saveAll.
        for (UUID userId : userIds) {
            Notification notification = new Notification();
            notification.setUser(userRepository.getReferenceById(userId));
            notification.setTitle(title);
            notification.setBody(body);
            notification.setType(type);
            notification.setReferenceId(ticketId);
            notification.setReferenceType(TICKET_REFERENCE_TYPE);
            batch.add(notification);
        }
        notificationRepository.saveAll(batch);
        log.info("Ticket {} — {} dispatched to {} recipients.", ticketId, type, batch.size());
    }

    /**
     * Maps a {@link Ticket} entity to a {@link TicketSummaryResponse}.
     *
     * @param ticket the ticket entity.
     * @return the summary DTO.
     */
    private TicketSummaryResponse toSummary(Ticket ticket) {
        return TicketSummaryResponse.builder()
                .id(ticket.getId())
                .apartment(buildApartmentRef(ticket))
                .submittedBy(buildUserRef(ticket.getSubmittedBy()))
                .category(ticket.getCategory())
                .title(ticket.getTitle())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .assignedToUser(ticket.getAssignedToUser() != null
                        ? buildUserRef(ticket.getAssignedToUser()) : null)
                .assignedToContractor(ticket.getAssignedToContractor() != null
                        ? buildContractorRef(ticket.getAssignedToContractor()) : null)
                .slaDeadline(ticket.getSlaDeadline())
                .slaBreached(isSlaBreached(ticket))
                .rating(ticket.getRating())
                .isPublic(ticket.isPublic())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    /**
     * Maps a public ticket to a redacted {@link TicketSummaryResponse} for residents
     * outside the ticket's household (G8): submitter shown as the «Cư dân» placeholder,
     * apartment reduced to the block name, assignee identities hidden.
     *
     * @param ticket the public ticket entity.
     * @return the redacted summary DTO.
     */
    private TicketSummaryResponse toRedactedSummary(Ticket ticket) {
        return TicketSummaryResponse.builder()
                .id(ticket.getId())
                .apartment(TicketSummaryResponse.ApartmentRef.builder()
                        .block(TicketSummaryResponse.BlockRef.builder()
                                .name(ticket.getApartment().getBlock().getName())
                                .build())
                        .build())
                .submittedBy(TicketSummaryResponse.UserRef.builder()
                        .fullName(REDACTED_SUBMITTER_LABEL)
                        .build())
                .category(ticket.getCategory())
                .title(ticket.getTitle())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .slaDeadline(ticket.getSlaDeadline())
                .slaBreached(isSlaBreached(ticket))
                .isPublic(ticket.isPublic())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    /**
     * Maps a {@link Ticket} entity to a {@link TicketDetailResponse} including photos and history.
     *
     * @param ticket the ticket entity.
     * @return the detail DTO.
     */
    private TicketDetailResponse toDetail(Ticket ticket) {
        return toDetail(ticket, true);
    }

    /**
     * Maps a {@link Ticket} entity to a {@link TicketDetailResponse}.
     *
     * @param ticket       the ticket entity.
     * @param includePhone whether to include the submitter's phone number in {@code submittedBy}.
     *                     Pass {@code false} for TECHNICIAN and BOARD_MEMBER callers (SEC-03, SEC-08).
     * @return the detail DTO.
     */
    private TicketDetailResponse toDetail(Ticket ticket, boolean includePhone) {
        List<TicketDetailResponse.PhotoResponse> photos = photoRepository
                .findByTicketId(ticket.getId())
                .stream()
                .map(p -> TicketDetailResponse.PhotoResponse.builder()
                        .id(p.getId())
                        .phase(p.getPhase())
                        .presignedUrl(fileStorageService.presign(p.getFileUrl()))
                        .fileName(p.getFileName())
                        .mimeType(p.getMimeType())
                        .fileSizeBytes(p.getFileSize())
                        .uploadedAt(p.getUploadedAt())
                        .build())
                .toList();

        List<TicketDetailResponse.StatusHistoryResponse> history = historyRepository
                .findByTicketIdOrderByChangedAtAsc(ticket.getId())
                .stream()
                .map(h -> TicketDetailResponse.StatusHistoryResponse.builder()
                        .id(h.getId())
                        .oldStatus(h.getOldStatus())
                        .newStatus(h.getNewStatus())
                        .changedBy(h.getChangedBy() != null
                                ? buildDetailUserRef(h.getChangedBy()) : null)
                        .notes(h.getNotes())
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();

        User submitter = ticket.getSubmittedBy();

        return TicketDetailResponse.builder()
                .id(ticket.getId())
                .apartment(buildDetailApartmentRef(ticket))
                .submittedBy(TicketDetailResponse.SubmitterRef.builder()
                        .id(submitter.getId())
                        .fullName(submitter.getFullName())
                        .phone(includePhone ? submitter.getPhone() : null)
                        .build())
                .category(ticket.getCategory())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .assignedToUser(ticket.getAssignedToUser() != null
                        ? buildDetailUserRef(ticket.getAssignedToUser()) : null)
                .assignedToContractor(ticket.getAssignedToContractor() != null
                        ? buildDetailContractorRef(ticket.getAssignedToContractor()) : null)
                .scheduledDate(ticket.getScheduledDate())
                .completedDate(ticket.getCompletedDate())
                .slaDeadline(ticket.getSlaDeadline())
                .slaBreached(isSlaBreached(ticket))
                .rating(ticket.getRating())
                .ratingComment(ticket.getRatingComment())
                .resolutionNotes(ticket.getResolutionNotes())
                .isPublic(ticket.isPublic())
                .photos(photos)
                .statusHistory(history)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    /**
     * Maps a public ticket to the redacted {@link TicketDetailResponse} returned to
     * residents outside the ticket's household (G8).
     *
     * <p>Visible: title, description, category, status, priority, block name,
     * createdAt, status-history timestamps + statuses, resolution notes.
     * Hidden: submitter identity (placeholder «Cư dân», no phone), apartment unit
     * number, assignee identities, photos (F-05 gate — never presigned here),
     * status-history changedBy + notes, rating and its comment.
     *
     * @param ticket the public ticket entity.
     * @return the redacted detail DTO.
     */
    private TicketDetailResponse toRedactedDetail(Ticket ticket) {
        // History keeps only timestamps and statuses — no staff names, no notes.
        List<TicketDetailResponse.StatusHistoryResponse> history = historyRepository
                .findByTicketIdOrderByChangedAtAsc(ticket.getId())
                .stream()
                .map(h -> TicketDetailResponse.StatusHistoryResponse.builder()
                        .id(h.getId())
                        .oldStatus(h.getOldStatus())
                        .newStatus(h.getNewStatus())
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();

        return TicketDetailResponse.builder()
                .id(ticket.getId())
                .apartment(TicketDetailResponse.ApartmentRef.builder()
                        .block(TicketDetailResponse.BlockRef.builder()
                                .name(ticket.getApartment().getBlock().getName())
                                .build())
                        .build())
                .submittedBy(TicketDetailResponse.SubmitterRef.builder()
                        .fullName(REDACTED_SUBMITTER_LABEL)
                        .build())
                .category(ticket.getCategory())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .slaBreached(isSlaBreached(ticket))
                .resolutionNotes(ticket.getResolutionNotes())
                .isPublic(ticket.isPublic())
                .redacted(true)
                .photos(List.of())
                .statusHistory(history)
                .createdAt(ticket.getCreatedAt())
                .build();
    }

    /**
     * Returns {@code true} when the ticket's SLA deadline is in the past and the ticket is still open.
     *
     * @param ticket the ticket to evaluate.
     * @return whether the SLA has been breached.
     */
    private boolean isSlaBreached(Ticket ticket) {
        return ticket.getSlaDeadline() != null
                && ticket.getSlaDeadline().isBefore(OffsetDateTime.now())
                && ticket.getStatus() != TicketStatus.DONE
                && ticket.getStatus() != TicketStatus.CANCELLED;
    }

    /**
     * Builds the {@link TicketSummaryResponse.ApartmentRef} for a ticket.
     *
     * @param ticket the ticket entity.
     * @return the apartment reference DTO.
     */
    private TicketSummaryResponse.ApartmentRef buildApartmentRef(Ticket ticket) {
        Apartment apt = ticket.getApartment();
        return TicketSummaryResponse.ApartmentRef.builder()
                .id(apt.getId())
                .unitNumber(apt.getUnitNumber())
                .block(TicketSummaryResponse.BlockRef.builder()
                        .name(apt.getBlock().getName())
                        .build())
                .build();
    }

    /**
     * Builds the {@link TicketDetailResponse.ApartmentRef} for a ticket.
     *
     * @param ticket the ticket entity.
     * @return the apartment reference DTO.
     */
    private TicketDetailResponse.ApartmentRef buildDetailApartmentRef(Ticket ticket) {
        Apartment apt = ticket.getApartment();
        return TicketDetailResponse.ApartmentRef.builder()
                .id(apt.getId())
                .unitNumber(apt.getUnitNumber())
                .block(TicketDetailResponse.BlockRef.builder()
                        .name(apt.getBlock().getName())
                        .build())
                .build();
    }

    /**
     * Builds a {@link TicketSummaryResponse.UserRef} from a user entity.
     *
     * @param user the user entity.
     * @return the user reference DTO.
     */
    private TicketSummaryResponse.UserRef buildUserRef(User user) {
        return TicketSummaryResponse.UserRef.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Builds a {@link TicketDetailResponse.UserRef} from a user entity.
     *
     * @param user the user entity.
     * @return the user reference DTO.
     */
    private TicketDetailResponse.UserRef buildDetailUserRef(User user) {
        return TicketDetailResponse.UserRef.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .build();
    }

    /**
     * Builds a {@link TicketSummaryResponse.ContractorRef} from a contractor entity.
     *
     * @param contractor the contractor entity.
     * @return the contractor reference DTO.
     */
    private TicketSummaryResponse.ContractorRef buildContractorRef(Contractor contractor) {
        return TicketSummaryResponse.ContractorRef.builder()
                .id(contractor.getId())
                .companyName(contractor.getCompanyName())
                .build();
    }

    /**
     * Builds a {@link TicketDetailResponse.ContractorRef} from a contractor entity.
     *
     * @param contractor the contractor entity.
     * @return the contractor reference DTO.
     */
    private TicketDetailResponse.ContractorRef buildDetailContractorRef(Contractor contractor) {
        return TicketDetailResponse.ContractorRef.builder()
                .id(contractor.getId())
                .companyName(contractor.getCompanyName())
                .build();
    }

    /**
     * Validates the MIME type of an uploaded file using magic-byte detection via Apache Tika.
     *
     * <p>This approach is resistant to bypass via a spoofed {@code Content-Type} header because
     * it inspects the actual file bytes rather than trusting the client-supplied value.
     *
     * @param file the uploaded multipart file.
     * @throws AppException with {@link ErrorCode#VALIDATION_ERROR} if the detected type is not allowed.
     */
    private void validateFileMimeType(MultipartFile file) {
        try {
            // SECURITY-FIX: detect MIME from file bytes, not from client-supplied Content-Type
            String detectedMime = TIKA.detect(file.getInputStream());
            if (!ALLOWED_MIME_TYPES.contains(detectedMime)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "File type not allowed. Only JPEG and PNG are accepted.");
            }
        } catch (java.io.IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unable to validate file type.");
        }
    }

    /**
     * Resolves the file extension from a MIME type.
     *
     * @param contentType the MIME type string.
     * @return {@code ".jpg"} for JPEG, {@code ".png"} for PNG, empty string otherwise.
     */
    private String resolveExtension(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        }
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        return "";
    }
}
