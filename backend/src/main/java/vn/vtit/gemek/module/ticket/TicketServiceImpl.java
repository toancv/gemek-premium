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
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.user.entity.User;
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

    /**
     * Constructs the service with all required dependencies via explicit constructor injection.
     *
     * @param ticketRepository     the ticket JPA repository.
     * @param photoRepository      the ticket photo JPA repository.
     * @param historyRepository    the ticket status history JPA repository.
     * @param apartmentRepository  the apartment JPA repository.
     * @param userRepository       the user JPA repository.
     * @param residentRepository   the resident JPA repository.
     * @param contractorRepository the contractor JPA repository.
     * @param fileStorageService   the MinIO file storage service.
     * @param notificationService  the notification service for in-app alerts.
     */
    public TicketServiceImpl(TicketRepository ticketRepository,
                             TicketPhotoRepository photoRepository,
                             TicketStatusHistoryRepository historyRepository,
                             ApartmentRepository apartmentRepository,
                             UserRepository userRepository,
                             ResidentRepository residentRepository,
                             ContractorRepository contractorRepository,
                             FileStorageService fileStorageService,
                             NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.photoRepository = photoRepository;
        this.historyRepository = historyRepository;
        this.apartmentRepository = apartmentRepository;
        this.userRepository = userRepository;
        this.residentRepository = residentRepository;
        this.contractorRepository = contractorRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    // =========================================================================
    // Read operations
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<TicketSummaryResponse> listTickets(UUID principalId, String role,
                                                           List<TicketStatus> statuses,
                                                           TicketCategory category,
                                                           TicketPriority priority,
                                                           UUID apartmentId,
                                                           Pageable pageable) {
        log.debug("listTickets — role={}, statuses={}, category={}", role, statuses, category);

        Specification<Ticket> spec = buildScopeSpec(principalId, role)
                .and(buildFilterSpec(statuses, category, priority, apartmentId));

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

        // SEC-03/SEC-08: strip submitter phone for roles without PII entitlement.
        boolean includePhone = !"TECHNICIAN".equals(role) && !"BOARD_MEMBER".equals(role);
        return toDetail(ticket, includePhone);
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

        // Compute SLA deadline from category; SUGGESTION_FEEDBACK has no SLA.
        Integer slaHours = SLA_HOURS.get(req.getCategory());
        if (slaHours != null) {
            ticket.setSlaDeadline(now.plusHours(slaHours));
        }

        Ticket saved = ticketRepository.save(ticket);

        // Initial history entry: null → NEW.
        appendHistory(saved, null, TicketStatus.NEW, submitter, null);

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

        // Notify the assigned technician.
        if (saved.getAssignedToUser() != null) {
            notificationService.createNotification(
                    saved.getAssignedToUser().getId(),
                    "Phản ánh được phân công",
                    "Phản ánh \"" + saved.getTitle() + "\" đã được phân công cho bạn.",
                    NotificationType.TICKET_ASSIGNED,
                    saved.getId(),
                    "Ticket");
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

        log.info("Ticket rated — id={}, rating={}", saved.getId(), saved.getRating());
        return toDetail(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assertPresignAccess(String fileUrl, UUID principalId, String role) {
        TicketPhoto photo = photoRepository.findByFileUrl(fileUrl)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "No photo record found for the requested key."));
        enforceReadAccess(photo.getTicket(), principalId, role);
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
     * Builds a role-scoped {@link Specification} that restricts the visible ticket set.
     *
     * <ul>
     *   <li>ADMIN / BOARD_MEMBER — no scope restriction.</li>
     *   <li>TECHNICIAN — assigned to principal OR status is NEW.</li>
     *   <li>RESIDENT — apartment matches the principal's active apartment.</li>
     * </ul>
     *
     * @param principalId the caller's UUID.
     * @param role        the caller's role string.
     * @return the scope specification.
     */
    private Specification<Ticket> buildScopeSpec(UUID principalId, String role) {
        if ("TECHNICIAN".equals(role)) {
            return (root, query, cb) -> cb.or(
                    cb.equal(root.get("assignedToUser").get("id"), principalId),
                    cb.equal(root.get("status"), TicketStatus.NEW)
            );
        }
        if ("RESIDENT".equals(role)) {
            // Resolve active apartment once; if none exists scope to an impossible condition.
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
                .photos(photos)
                .statusHistory(history)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
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
