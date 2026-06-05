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
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.dto.MoveOutRequest;
import vn.vtit.gemek.module.resident.dto.ResidentHistoryResponse;
import vn.vtit.gemek.module.resident.dto.ResidentResponse;
import vn.vtit.gemek.module.resident.dto.UpdateResidentRequest;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentHistory;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.mapper.ResidentMapper;
import vn.vtit.gemek.module.resident.repository.ResidentHistoryRepository;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    private final ResidentRepository residentRepository;
    private final ResidentHistoryRepository historyRepository;
    private final ApartmentRepository apartmentRepository;
    private final UserRepository userRepository;
    private final ResidentMapper residentMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param residentRepository  the resident JPA repository.
     * @param historyRepository   the resident history JPA repository.
     * @param apartmentRepository the apartment JPA repository.
     * @param userRepository      the user JPA repository.
     * @param residentMapper      the MapStruct resident mapper.
     * @param passwordEncoder     the BCrypt password encoder.
     */
    public ResidentServiceImpl(ResidentRepository residentRepository,
                               ResidentHistoryRepository historyRepository,
                               ApartmentRepository apartmentRepository,
                               UserRepository userRepository,
                               ResidentMapper residentMapper,
                               PasswordEncoder passwordEncoder) {
        this.residentRepository = residentRepository;
        this.historyRepository = historyRepository;
        this.apartmentRepository = apartmentRepository;
        this.userRepository = userRepository;
        this.residentMapper = residentMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResidentResponse getMyResident(UUID userId) {
        log.debug("Getting own resident record — userId={}", userId);

        Resident resident = residentRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "No active residency found for the current user."));

        return residentMapper.toResponse(resident);
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
    @Transactional
    public ResidentResponse createResident(CreateResidentRequest req, UUID principalId) {
        log.debug("Creating resident — email={}, apartmentId={}", req.getEmail(), req.getApartmentId());

        // Email uniqueness check — reject before touching the DB to keep the transaction clean.
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new AppException(ErrorCode.CONFLICT, "Email already registered: " + req.getEmail());
        }

        Apartment apartment = apartmentRepository.findById(req.getApartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Apartment not found: " + req.getApartmentId()));

        // Create the user account. Password is logged at DEBUG only as a masked marker; plaintext never logged.
        User user = new User();
        user.setEmail(req.getEmail());
        user.setFullName(req.getFullName());
        user.setPhone(req.getPhone());
        user.setDateOfBirth(req.getDateOfBirth());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(UserRole.RESIDENT);
        user.setActive(true);
        User savedUser = userRepository.save(user);
        log.debug("User account created — userId={}, role=RESIDENT", savedUser.getId());

        // When setting as primary contact, clear the flag on all other active residents.
        if (req.isPrimaryContact()) {
            clearPrimaryContactInApartment(apartment.getId());
        }

        OffsetDateTime now = OffsetDateTime.now();
        Resident resident = new Resident();
        resident.setUser(savedUser);
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

        log.info("Resident created — id={}, userId={}", saved.getId(), savedUser.getId());
        return residentMapper.toResponse(saved);
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
            throw new AppException(ErrorCode.CONFLICT, "Resident has already moved out.");
        }

        resident.setMoveOutDate(req.getMoveOutDate());

        // Clear primary contact flag when the resident leaves.
        if (resident.isPrimaryContact()) {
            resident.setPrimaryContact(false);
        }

        resident.setUpdatedAt(OffsetDateTime.now());
        Resident saved = residentRepository.save(resident);

        appendHistory(saved, "MOVED_OUT", req.getMoveOutDate(), principalId, req.getNotes());

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
