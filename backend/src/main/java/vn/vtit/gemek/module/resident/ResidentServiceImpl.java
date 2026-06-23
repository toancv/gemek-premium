/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.exception.ReuseConfirmationRequiredException;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.dto.MoveOutRequest;
import vn.vtit.gemek.module.resident.dto.ResidentHistoryResponse;
import vn.vtit.gemek.module.resident.dto.ResidentLookupResponse;
import vn.vtit.gemek.module.resident.dto.ResidentResponse;
import vn.vtit.gemek.module.resident.dto.UpdateResidentRequest;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentHistory;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.mapper.ResidentMapper;
import vn.vtit.gemek.module.resident.repository.ResidentHistoryRepository;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.common.util.PhoneUtils;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implementation of {@link ResidentService}.
 *
 * <p>All write operations are transactional. Read operations run in
 * {@code readOnly} transactions to allow the JPA provider to skip
 * dirty-checking overhead.
 */
@Service
@Transactional(readOnly = true)
public class ResidentServiceImpl implements ResidentService {

    private static final Logger log = LoggerFactory.getLogger(ResidentServiceImpl.class);

    /**
     * Password complexity rule for the NEW branch — min 8 chars with upper, lower, digit, and special.
     * Mirrors the former {@code @Pattern} on {@code CreateResidentRequest.password}, relocated here because
     * the password is required only when provisioning a brand-new user (see DTO conditional-validation note).
     */
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$");

    private final ResidentRepository residentRepository;
    private final ResidentHistoryRepository historyRepository;
    private final ApartmentRepository apartmentRepository;
    private final UserRepository userRepository;
    private final ResidentMapper residentMapper;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param residentRepository     the resident JPA repository.
     * @param historyRepository      the resident history JPA repository.
     * @param apartmentRepository    the apartment JPA repository.
     * @param userRepository         the user JPA repository.
     * @param residentMapper         the MapStruct resident mapper.
     * @param passwordEncoder        the BCrypt password encoder.
     * @param notificationRepository the notification JPA repository for household dispatch (N3 C9).
     */
    public ResidentServiceImpl(ResidentRepository residentRepository,
                               ResidentHistoryRepository historyRepository,
                               ApartmentRepository apartmentRepository,
                               UserRepository userRepository,
                               ResidentMapper residentMapper,
                               PasswordEncoder passwordEncoder,
                               NotificationRepository notificationRepository) {
        this.residentRepository = residentRepository;
        this.historyRepository = historyRepository;
        this.apartmentRepository = apartmentRepository;
        this.userRepository = userRepository;
        this.residentMapper = residentMapper;
        this.passwordEncoder = passwordEncoder;
        this.notificationRepository = notificationRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ResidentResponse> getMyResident(UUID userId) {
        log.debug("Getting own resident records — userId={}", userId);

        // Multi-residency: return ALL active residencies (0, 1, or 2+). An empty list is a valid
        // state (logged-in user with no active residency) — returned as [], not a 404.
        return residentRepository.findAllActiveByUserId(userId).stream()
                .map(residentMapper::toResponse)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ResidentResponse> listResidents(UUID apartmentId, ResidentType type,
                                                         Boolean isActive, String search, Pageable pageable) {
        log.debug("Listing residents — apartmentId={}, type={}, isActive={}, search={}", apartmentId, type, isActive, search);

        Specification<Resident> spec = buildSpecification(apartmentId, type, isActive, search);
        Page<ResidentResponse> page = residentRepository.findAll(spec, pageable)
                .map(residentMapper::toResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResidentLookupResponse lookupByPhone(String phone, UUID apartmentId) {
        log.debug("Resident lookup — apartmentId={}", apartmentId);

        // Normalize to canonical 0xxxxxxxxx; throws VALIDATION_ERROR on invalid format.
        String normalizedPhone = PhoneUtils.normalize(phone);

        Optional<User> existing = userRepository.findByPhone(normalizedPhone);
        if (existing.isEmpty()) {
            // Phone is free — a brand-new user/resident will be created on place.
            return ResidentLookupResponse.builder()
                    .status(ResidentLookupResponse.LookupStatus.NEW)
                    .activeApartments(List.of())
                    .build();
        }
        return buildLookup(existing.get(), apartmentId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ResidentResponse createResident(CreateResidentRequest req, UUID principalId) {
        log.debug("Place resident — apartmentId={}, confirmReuse={}", req.getApartmentId(), req.isConfirmReuse());

        // Normalize to canonical 0xxxxxxxxx; throws VALIDATION_ERROR on invalid format.
        String normalizedPhone = PhoneUtils.normalize(req.getPhone());

        Apartment apartment = apartmentRepository.findById(req.getApartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Apartment not found: " + req.getApartmentId()));

        Optional<User> existing = userRepository.findByPhone(normalizedPhone);

        // BRANCH 1 — NEW: the phone is unused → provision a fresh user + residency (today's behavior).
        if (existing.isEmpty()) {
            User savedUser = provisionNewUser(req, normalizedPhone);
            return placeResidentRow(savedUser, apartment, req, principalId);
        }

        // The phone belongs to an existing user → REUSE path. Identity is server-derived from this user and
        // is NEVER overwritten by request values (IDOR-safe — the client cannot force or alter identity).
        User user = existing.get();

        // BRANCH 2 — already actively residing in the TARGET apartment: reject, create nothing. (A second
        // active row for the same (user, apartment) pair is the real invariant the relaxed index still guards;
        // this explicit pre-check surfaces it as a clean 409 instead of a constraint violation.)
        if (residentRepository.existsActiveByUserIdAndApartmentId(user.getId(), apartment.getId())) {
            throw new AppException(ErrorCode.ALREADY_ACTIVE_IN_APARTMENT, "Cư dân này đang ở căn hộ này rồi.");
        }

        // BRANCH 3 — known user, not active in target, no explicit confirmation: return the matched person's
        // identifying info so the FE can confirm. Nothing is created. The server NEVER trusts the FE's step-1
        // lookup — this re-resolves the phone independently before any write.
        if (!req.isConfirmReuse()) {
            throw new ReuseConfirmationRequiredException(buildLookup(user, apartment.getId()),
                    "Phone belongs to an existing user; confirm reuse to add a new residency.");
        }

        // BRANCH 4 — confirmed reuse: reactivate (enabled-only) when disabled, then add the new residency.
        // Reactivate touches ONLY the enabled flag — role and password are left untouched (a returning user
        // logs in with their old credentials); see DECISIONS "[hoãn] force-password-reset" note.
        if (!user.isActive()) {
            user.setActive(true);
            userRepository.save(user);
            log.info("Place resident — reactivated user {} (enabled-only).", user.getId());
        }
        return placeResidentRow(user, apartment, req, principalId);
    }

    /**
     * Provisions a brand-new RESIDENT user from the request (NEW branch only).
     *
     * <p>Enforces the fields that are required only when the phone is new — {@code fullName},
     * {@code password} (presence + complexity), {@code dateOfBirth} — here rather than via bean validation,
     * because validation cannot branch on a DB phone lookup. Email uniqueness is checked for the optional
     * email before the insert.
     *
     * @param req             the place request.
     * @param normalizedPhone the canonical phone (already normalized).
     * @return the persisted new user.
     */
    private User provisionNewUser(CreateResidentRequest req, String normalizedPhone) {
        if (req.getFullName() == null || req.getFullName().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "fullName is required.");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "password is required.");
        }
        if (!PASSWORD_PATTERN.matcher(req.getPassword()).matches()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Password must be at least 8 characters and include upper, lower, digit, and special character.");
        }
        if (req.getDateOfBirth() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "dateOfBirth is required.");
        }
        // Email uniqueness check — reject before touching the DB to keep the transaction clean.
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email address is already registered.");
        }

        // Create the user account. Password is logged at DEBUG only as a masked marker; plaintext never logged.
        User user = new User();
        user.setEmail(req.getEmail());
        user.setFullName(req.getFullName());
        user.setPhone(normalizedPhone);
        user.setDateOfBirth(req.getDateOfBirth());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(UserRole.RESIDENT);
        user.setActive(true);
        User savedUser = userRepository.save(user);
        log.debug("User account created — userId={}, role=RESIDENT", savedUser.getId());
        return savedUser;
    }

    /**
     * Inserts one active residency row for the given user + apartment and writes its history + household
     * notifications. Shared by the NEW and confirmed-REUSE branches so both produce an identical residency
     * side-effect (MOVED_IN event, primary-contact handling, household dispatch).
     *
     * @param user        the user (newly provisioned or reused) to attach the residency to.
     * @param apartment   the target apartment.
     * @param req         the place request (type, moveInDate, primaryContact, notes).
     * @param principalId the acting admin's UUID (history actor + notification exclusion).
     * @return the mapped resident response.
     */
    private ResidentResponse placeResidentRow(User user, Apartment apartment,
                                              CreateResidentRequest req, UUID principalId) {
        // When setting as primary contact, clear the flag on all other active residents.
        if (req.isPrimaryContact()) {
            clearPrimaryContactInApartment(apartment.getId());
        }

        OffsetDateTime now = OffsetDateTime.now();
        Resident resident = new Resident();
        resident.setUser(user);
        resident.setApartment(apartment);
        resident.setType(req.getType());
        resident.setMoveInDate(req.getMoveInDate());
        resident.setPrimaryContact(req.isPrimaryContact());
        resident.setNotes(req.getNotes());
        resident.setCreatedAt(now);
        resident.setUpdatedAt(now);

        Resident saved = residentRepository.save(resident);

        appendHistory(saved, "MOVED_IN", req.getMoveInDate(), principalId, req.getNotes());
        if (req.isPrimaryContact()) {
            appendHistory(saved, "PRIMARY_CONTACT_SET", req.getMoveInDate(), principalId, null);
        }

        // C9 (N3): tell the existing household about the new member. Excludes the
        // placed user (their own arrival) and the actor (uniform actor-exclusion rule).
        dispatchHouseholdNotifications(saved, user, apartment, principalId);

        log.info("Resident placed — id={}, userId={}", saved.getId(), user.getId());
        return residentMapper.toResponse(saved);
    }

    /**
     * Builds the minimal lookup view for an existing user: branch status + display name + the apartments the
     * user currently actively resides in. {@code targetApartmentId} (when non-null) lets the result surface
     * the ALREADY_HERE case. Returns only name + active-apartment identifiers — never full PII.
     *
     * @param user              the matched existing user.
     * @param targetApartmentId optional target apartment for ALREADY_HERE detection; may be null.
     * @return the lookup result.
     */
    private ResidentLookupResponse buildLookup(User user, UUID targetApartmentId) {
        List<Resident> active = residentRepository.findAllActiveByUserId(user.getId());

        // ALREADY_HERE takes precedence when a target apartment is supplied and the user is active in it.
        boolean alreadyHere = targetApartmentId != null
                && active.stream().anyMatch(r -> targetApartmentId.equals(r.getApartment().getId()));

        ResidentLookupResponse.LookupStatus status;
        if (alreadyHere) {
            status = ResidentLookupResponse.LookupStatus.ALREADY_HERE;
        } else if (active.isEmpty()) {
            status = ResidentLookupResponse.LookupStatus.MOVED_OUT;
        } else {
            status = ResidentLookupResponse.LookupStatus.ACTIVE_ELSEWHERE;
        }

        return ResidentLookupResponse.builder()
                .status(status)
                .displayName(user.getFullName())
                .activeApartments(toApartmentRefs(active))
                .build();
    }

    /**
     * Maps active residency rows to minimal apartment references (id + unit number + block name).
     *
     * @param residents active residency rows (apartment + block already fetched).
     * @return apartment references for the lookup response.
     */
    private List<ResidentLookupResponse.ApartmentRef> toApartmentRefs(List<Resident> residents) {
        return residents.stream()
                .map(r -> ResidentLookupResponse.ApartmentRef.builder()
                        .id(r.getApartment().getId())
                        .unitNumber(r.getApartment().getUnitNumber())
                        .blockName(r.getApartment().getBlock() != null
                                ? r.getApartment().getBlock().getName() : null)
                        .build())
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResidentResponse getResident(UUID id, UUID principalId, String role) {
        log.debug("Getting resident id={}", id);

        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Resident not found: " + id));

        // RESIDENT callers may only view their own record.
        if ("RESIDENT".equals(role) && !resident.getUser().getId().equals(principalId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Access denied to this resident record.");
        }

        return residentMapper.toResponse(resident);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ResidentResponse updateResident(UUID id, UpdateResidentRequest req, UUID principalId) {
        log.debug("Updating resident id={}", id);

        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Resident not found: " + id));

        LocalDate today = LocalDate.now();

        // Detect and record type change before applying the update.
        if (req.getType() != null && !req.getType().equals(resident.getType())) {
            resident.setType(req.getType());
            appendHistory(resident, "TYPE_CHANGED", today, principalId, req.getNotes());
        }

        // When isPrimaryContact transitions to true, clear others and write history.
        if (Boolean.TRUE.equals(req.getIsPrimaryContact()) && !resident.isPrimaryContact()) {
            clearPrimaryContactInApartment(resident.getApartment().getId());
            resident.setPrimaryContact(true);
            appendHistory(resident, "PRIMARY_CONTACT_SET", today, principalId, null);
        } else if (req.getIsPrimaryContact() != null) {
            resident.setPrimaryContact(req.getIsPrimaryContact());
        }

        if (req.getNotes() != null) {
            resident.setNotes(req.getNotes());
        }

        resident.setUpdatedAt(OffsetDateTime.now());
        Resident saved = residentRepository.save(resident);

        log.info("Resident updated — id={}", saved.getId());
        return residentMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ResidentResponse moveOut(UUID id, MoveOutRequest req, UUID principalId) {
        log.debug("Move-out resident id={}", id);

        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Resident not found: " + id));

        // Guard against processing an already-moved-out resident.
        if (resident.getMoveOutDate() != null) {
            throw new AppException(ErrorCode.RESIDENT_ALREADY_MOVED_OUT, "Resident has already moved out.");
        }

        resident.setMoveOutDate(req.getMoveOutDate());

        // Clear primary contact flag when the resident leaves.
        if (resident.isPrimaryContact()) {
            resident.setPrimaryContact(false);
        }

        resident.setUpdatedAt(OffsetDateTime.now());
        Resident saved = residentRepository.save(resident);

        appendHistory(saved, "MOVED_OUT", req.getMoveOutDate(), principalId, req.getNotes());

        // Conditionally deactivate the linked login account in the SAME transaction.
        // The move-out above already set moveOutDate; existsActiveByUserId therefore no
        // longer counts THIS residency, so it answers "does the user still have ANY other
        // active residency?". Lock the account only when the answer is no — a user who still
        // lives in another apartment keeps their login. resident.user is non-null per schema
        // (user_id NOT NULL); the null guard is defensive. If the deactivation write fails,
        // the RuntimeException propagates and the whole move-out rolls back (atomic).
        User residentUser = saved.getUser();
        if (residentUser != null && !residentRepository.existsActiveByUserId(residentUser.getId())) {
            residentUser.setActive(false);
            userRepository.save(residentUser);
            log.info("Resident move-out — user {} deactivated (no remaining active residency).",
                    residentUser.getId());
        }

        log.info("Resident moved out — id={}, moveOutDate={}", saved.getId(), saved.getMoveOutDate());
        return residentMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ResidentHistoryResponse> getResidentHistory(UUID residentId, Pageable pageable) {
        log.debug("Getting history for resident id={}", residentId);

        // Verify the resident exists before querying history.
        Resident resident = residentRepository.findById(residentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Resident not found: " + residentId));

        Page<ResidentHistoryResponse> page =
                historyRepository.findByUserIdOrderByEventDateDesc(resident.getUser().getId(), pageable)
                        .map(residentMapper::toHistoryResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ResidentHistoryResponse> getApartmentHistory(UUID apartmentId, Pageable pageable) {
        log.debug("Getting history for apartment id={}", apartmentId);

        // Verify the apartment exists before querying history.
        if (!apartmentRepository.existsById(apartmentId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Apartment not found: " + apartmentId);
        }

        Page<ResidentHistoryResponse> page =
                historyRepository.findByApartmentIdOrderByEventDateDesc(apartmentId, pageable)
                        .map(residentMapper::toHistoryResponse);
        return PageResponse.of(page);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a JPA {@link Specification} from optional filter parameters.
     *
     * <p>For data queries (non-count), fetch joins are added for user, apartment,
     * and apartment.block to avoid N+1 queries when mapping the response.
     *
     * <p>The search predicate uses the Criteria API with a pre-built literal pattern
     * to avoid the Hibernate 6 null→bytea parameter-type bug that affects JPQL
     * named parameters inside {@code LOWER()} expressions.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param type        optional resident type filter.
     * @param isActive    optional active/inactive filter.
     * @param search      optional case-insensitive substring on user fullName or email.
     * @return the composed specification.
     */
    private Specification<Resident> buildSpecification(UUID apartmentId, ResidentType type,
                                                        Boolean isActive, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Eager-fetch associated entities in data queries to prevent N+1.
            // Fetch is not valid in COUNT queries (Long result type).
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                Fetch<Resident, Apartment> aptFetch = root.fetch("apartment", JoinType.LEFT);
                aptFetch.fetch("block", JoinType.LEFT);
                root.fetch("user", JoinType.LEFT);
            }

            if (apartmentId != null) {
                predicates.add(cb.equal(root.get("apartment").get("id"), apartmentId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (Boolean.TRUE.equals(isActive)) {
                predicates.add(cb.isNull(root.get("moveOutDate")));
            } else if (Boolean.FALSE.equals(isActive)) {
                predicates.add(cb.isNotNull(root.get("moveOutDate")));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("user").get("fullName")), pattern),
                        cb.like(cb.lower(root.get("user").get("email")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * C9 (N3): creates one {@code HOUSEHOLD_MEMBER_ADDED} notification row per existing
     * active household member of the apartment, in a single batched insert.
     *
     * <p>Same pattern as the announcement/ticket dispatch: user FKs attached via
     * {@code getReferenceById}, one {@code saveAll}, no logging or I/O in the loop,
     * all inside the calling create transaction. Excluded: the newly created user and
     * the acting user. An empty recipient set (first resident of an empty apartment)
     * is a no-op — no {@code saveAll} call.
     *
     * @param resident    the just-created resident row (notification reference).
     * @param newUser     the newly created user (excluded recipient, body name source).
     * @param apartment   the apartment (body unit-number source).
     * @param principalId the acting user's UUID (excluded recipient).
     */
    private void dispatchHouseholdNotifications(Resident resident, User newUser,
                                                Apartment apartment, UUID principalId) {
        List<UUID> recipientIds = residentRepository.findActiveByApartmentId(apartment.getId())
                .stream()
                .map(member -> member.getUser().getId())
                .filter(userId -> !userId.equals(newUser.getId()) && !userId.equals(principalId))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        List<Notification> batch = new ArrayList<>(recipientIds.size());
        // Build the full batch in memory, then one saveAll.
        for (UUID userId : recipientIds) {
            Notification notification = new Notification();
            notification.setUser(userRepository.getReferenceById(userId));
            notification.setTitle("Thành viên mới");
            notification.setBody("Cư dân " + newUser.getFullName() + " đã được thêm vào căn hộ "
                    + apartment.getUnitNumber() + ".");
            notification.setType(NotificationType.HOUSEHOLD_MEMBER_ADDED);
            notification.setReferenceId(resident.getId());
            notification.setReferenceType("Resident");
            batch.add(notification);
        }
        notificationRepository.saveAll(batch);
        log.info("Resident {} — household notice dispatched to {} recipients.",
                resident.getId(), batch.size());
    }

    /**
     * Clears the {@code isPrimaryContact} flag on all active residents in the given apartment.
     *
     * <p>Called before designating a new primary contact to ensure at most one
     * active resident holds the flag per apartment.
     *
     * @param apartmentId the apartment UUID whose active residents should be updated.
     */
    private void clearPrimaryContactInApartment(UUID apartmentId) {
        List<Resident> activeResidents = residentRepository.findActiveByApartmentId(apartmentId);
        for (Resident r : activeResidents) {
            if (r.isPrimaryContact()) {
                r.setPrimaryContact(false);
                r.setUpdatedAt(OffsetDateTime.now());
                residentRepository.save(r);
            }
        }
    }

    /**
     * Appends a single {@link ResidentHistory} entry for the given resident and event.
     *
     * @param resident    the resident the event belongs to.
     * @param event       the event type string (MOVED_IN, MOVED_OUT, TYPE_CHANGED, PRIMARY_CONTACT_SET).
     * @param eventDate   the date on which the event occurred.
     * @param changedById UUID of the user who performed the change; may be {@code null}.
     * @param notes       optional notes to attach to the history entry.
     */
    private void appendHistory(Resident resident, String event, LocalDate eventDate,
                               UUID changedById, String notes) {
        User changedBy = null;
        if (changedById != null) {
            // Load the user reference; ignore if not found to keep history write non-blocking.
            changedBy = userRepository.findById(changedById).orElse(null);
        }

        ResidentHistory history = new ResidentHistory();
        history.setApartment(resident.getApartment());
        history.setUser(resident.getUser());
        history.setType(resident.getType());
        history.setEvent(event);
        history.setEventDate(eventDate);
        history.setChangedBy(changedBy);
        history.setNotes(notes);
        history.setCreatedAt(OffsetDateTime.now());

        historyRepository.save(history);
        log.debug("History appended — event={}, residentId={}", event, resident.getId());
    }
}
