/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.amenity.dto.AmenityBookingResponse;
import vn.vtit.gemek.module.amenity.dto.AmenityResponse;
import vn.vtit.gemek.module.amenity.dto.ApproveRejectRequest;
import vn.vtit.gemek.module.amenity.dto.AvailabilityResponse;
import vn.vtit.gemek.module.amenity.dto.CreateAmenityRequest;
import vn.vtit.gemek.module.amenity.dto.CreateBookingRequest;
import vn.vtit.gemek.module.amenity.dto.UpdateAmenityRequest;
import vn.vtit.gemek.module.amenity.entity.Amenity;
import vn.vtit.gemek.module.amenity.entity.AmenityBooking;
import vn.vtit.gemek.module.amenity.entity.BookingStatus;
import vn.vtit.gemek.module.amenity.repository.AmenityBookingRepository;
import vn.vtit.gemek.module.amenity.repository.AmenityRepository;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AmenityService}.
 *
 * <p>All read operations run under the class-level {@code readOnly} transaction.
 * Write operations override with {@code @Transactional} to acquire a read-write transaction.
 *
 * <p>Double-booking prevention uses a pessimistic write lock via
 * {@link AmenityBookingRepository#findConflicting}, which acquires a row-level lock on
 * all overlapping bookings before the new record is inserted — safe for single-instance
 * deployment as documented in ARCHITECTURE.md section 4.5.
 */
@Service
@Transactional(readOnly = true)
public class AmenityServiceImpl implements AmenityService {

    private static final Logger log = LoggerFactory.getLogger(AmenityServiceImpl.class);

    /** Minimum allowed booking duration in minutes (SEC-11). */
    private static final int MIN_BOOKING_DURATION_MINUTES = 30;

    /** Maximum number of days in advance a booking may be placed (SEC-12). */
    private static final int MAX_ADVANCE_DAYS = 14;

    private final AmenityRepository amenityRepository;
    private final AmenityBookingRepository bookingRepository;
    private final ResidentRepository residentRepository;
    private final UserRepository userRepository;

    /**
     * Constructs the service with all required repositories via constructor injection.
     *
     * @param amenityRepository  the amenity JPA repository.
     * @param bookingRepository  the amenity booking JPA repository.
     * @param residentRepository the resident JPA repository.
     * @param userRepository     the user JPA repository.
     */
    public AmenityServiceImpl(AmenityRepository amenityRepository,
                               AmenityBookingRepository bookingRepository,
                               ResidentRepository residentRepository,
                               UserRepository userRepository) {
        this.amenityRepository = amenityRepository;
        this.bookingRepository = bookingRepository;
        this.residentRepository = residentRepository;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // Amenity CRUD
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>When {@code active} is non-null, applies an equality filter on {@code is_active}.
     * Otherwise returns all amenities regardless of active status.
     */
    @Override
    public PageResponse<AmenityResponse> listAmenities(Boolean active, Pageable pageable) {
        log.debug("listAmenities — active={}", active);

        Page<Amenity> page;
        if (active != null) {
            // Filter by active flag using a specification predicate.
            Specification<Amenity> spec = (root, query, cb) ->
                    cb.equal(root.get("active"), active);
            page = amenityRepository.findAll(spec, pageable);
        } else {
            page = amenityRepository.findAll(pageable);
        }

        return PageResponse.of(page.map(this::toAmenityResponse));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks name uniqueness before persisting.
     */
    @Override
    @Transactional
    public AmenityResponse createAmenity(CreateAmenityRequest req) {
        log.debug("createAmenity — name={}", req.getName());

        // Enforce unique name constraint at the service layer before the INSERT.
        if (amenityRepository.existsByName(req.getName())) {
            throw new AppException(ErrorCode.AMENITY_NAME_EXISTS,
                    "Amenity with name '" + req.getName() + "' already exists.");
        }

        Amenity amenity = new Amenity();
        amenity.setName(req.getName());
        amenity.setDescription(req.getDescription());
        amenity.setLocation(req.getLocation());
        amenity.setCapacity(req.getCapacity());
        amenity.setOpeningTime(req.getOpeningTime());
        amenity.setClosingTime(req.getClosingTime());
        amenity.setMaxDailyBookingsPerResident(req.getMaxDailyBookingsPerResident());
        amenity.setRequiresApproval(req.isRequiresApproval());
        // New amenities are active by default.
        amenity.setActive(true);

        Amenity saved = amenityRepository.save(amenity);
        log.info("Amenity created — id={}, name={}", saved.getId(), saved.getName());
        return toAmenityResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AmenityResponse getAmenity(UUID id) {
        log.debug("getAmenity — id={}", id);
        return toAmenityResponse(requireAmenity(id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only non-null fields in the request are applied to the entity.
     * Name uniqueness is checked excluding the current record's own ID.
     */
    @Override
    @Transactional
    public AmenityResponse updateAmenity(UUID id, UpdateAmenityRequest req) {
        log.debug("updateAmenity — id={}", id);

        Amenity amenity = requireAmenity(id);

        // Validate new name uniqueness only when the name is being changed.
        if (req.getName() != null && !req.getName().equals(amenity.getName())) {
            if (amenityRepository.existsByNameAndIdNot(req.getName(), id)) {
                throw new AppException(ErrorCode.AMENITY_NAME_EXISTS,
                        "Amenity with name '" + req.getName() + "' already exists.");
            }
            amenity.setName(req.getName());
        }

        if (req.getDescription() != null) {
            amenity.setDescription(req.getDescription());
        }
        if (req.getLocation() != null) {
            amenity.setLocation(req.getLocation());
        }
        if (req.getCapacity() != null) {
            amenity.setCapacity(req.getCapacity());
        }
        if (req.getOpeningTime() != null) {
            amenity.setOpeningTime(req.getOpeningTime());
        }
        if (req.getClosingTime() != null) {
            amenity.setClosingTime(req.getClosingTime());
        }
        if (req.getMaxDailyBookingsPerResident() != null) {
            amenity.setMaxDailyBookingsPerResident(req.getMaxDailyBookingsPerResident());
        }
        if (req.getRequiresApproval() != null) {
            amenity.setRequiresApproval(req.getRequiresApproval());
        }

        Amenity saved = amenityRepository.save(amenity);
        log.info("Amenity updated — id={}", saved.getId());
        return toAmenityResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets {@code isActive = false} and cancels all PENDING future bookings for this amenity.
     * APPROVED future bookings are also cancelled. Past bookings are left untouched.
     */
    @Override
    @Transactional
    public void deactivateAmenity(UUID id) {
        log.debug("deactivateAmenity — id={}", id);

        Amenity amenity = requireAmenity(id);
        amenity.setActive(false);
        amenityRepository.save(amenity);

        // Cancel all pending/approved future bookings for this amenity.
        LocalDate today = LocalDate.now();
        Specification<AmenityBooking> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("amenity").get("id"), id),
                cb.greaterThan(root.get("bookingDate"), today),
                root.get("status").in(BookingStatus.PENDING, BookingStatus.APPROVED)
        );

        List<AmenityBooking> futureBookings = bookingRepository.findAll(spec);
        for (AmenityBooking booking : futureBookings) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setRejectionReason("Amenity has been deactivated.");
        }
        if (!futureBookings.isEmpty()) {
            bookingRepository.saveAll(futureBookings);
            log.info("deactivateAmenity — cancelled {} future booking(s) for amenity id={}",
                    futureBookings.size(), id);
        }

        log.info("Amenity deactivated — id={}", id);
    }

    // =========================================================================
    // Availability
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Loads all PENDING and APPROVED bookings for the amenity on the given date
     * and maps them to {@link AvailabilityResponse.SlotInfo} entries.
     */
    @Override
    public AvailabilityResponse getAvailability(UUID amenityId, LocalDate date) {
        log.debug("getAvailability — amenityId={}, date={}", amenityId, date);

        Amenity amenity = requireAmenity(amenityId);
        List<AmenityBooking> bookings = bookingRepository.findByAmenityAndDate(amenityId, date);

        List<AvailabilityResponse.SlotInfo> slots = bookings.stream()
                .map(b -> AvailabilityResponse.SlotInfo.builder()
                        .start(b.getStartTime())
                        .end(b.getEndTime())
                        .status(b.getStatus())
                        .build())
                .collect(Collectors.toList());

        return AvailabilityResponse.builder()
                .amenityId(amenityId)
                .date(date)
                .openingTime(amenity.getOpeningTime())
                .closingTime(amenity.getClosingTime())
                .bookedSlots(slots)
                .build();
    }

    // =========================================================================
    // Booking lifecycle
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Business rules enforced in order:
     * <ol>
     *   <li>Resolve the active resident record for the principal.</li>
     *   <li>Validate requested slot is within the amenity's operating hours.</li>
     *   <li>Check daily booking limit for this resident on this date.</li>
     *   <li>Acquire pessimistic lock and check for time-slot conflicts.</li>
     *   <li>Persist and auto-approve if {@code requiresApproval = false}.</li>
     * </ol>
     */
    @Override
    @Transactional
    public AmenityBookingResponse createBooking(CreateBookingRequest req, UUID principalId) {
        log.debug("createBooking — amenityId={}, principalId={}", req.getAmenityId(), principalId);

        // 1. Resolve the resident record for the principal.
        Resident resident = residentRepository.findActiveByUserId(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "No active resident record found for user: " + principalId));

        Amenity amenity = requireAmenity(req.getAmenityId());
        Apartment apartment = resident.getApartment();

        // 2. Validate booking date is present or future (SEC-22) and within the advance window (SEC-12).
        LocalDate today = LocalDate.now();
        if (req.getBookingDate().isBefore(today)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Booking date must not be in the past.");
        }
        if (req.getBookingDate().isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Bookings may not be placed more than " + MAX_ADVANCE_DAYS + " days in advance.");
        }

        // 3. Validate the requested time slot is within the amenity's operating hours.
        if (req.getStartTime().isBefore(amenity.getOpeningTime())
                || req.getEndTime().isAfter(amenity.getClosingTime())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Requested time slot is outside amenity operating hours ("
                            + amenity.getOpeningTime() + " – " + amenity.getClosingTime() + ").");
        }

        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "startTime must be before endTime.");
        }

        // SEC-11: enforce minimum booking duration to prevent slot exhaustion.
        long durationMinutes = Duration.between(req.getStartTime(), req.getEndTime()).toMinutes();
        if (durationMinutes < MIN_BOOKING_DURATION_MINUTES) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Minimum booking duration is " + MIN_BOOKING_DURATION_MINUTES + " minutes.");
        }

        // 3. Enforce per-resident daily booking limit for this amenity.
        long dailyCount = bookingRepository.countDailyBookings(resident.getId(), req.getBookingDate());
        if (dailyCount >= amenity.getMaxDailyBookingsPerResident()) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Daily booking limit of " + amenity.getMaxDailyBookingsPerResident()
                            + " reached for this amenity on " + req.getBookingDate() + ".");
        }

        // 4. Pessimistic-lock conflict check — prevents double-booking under concurrent load.
        List<AmenityBooking> conflicts = bookingRepository.findConflicting(
                amenity.getId(), req.getBookingDate(), req.getStartTime(), req.getEndTime());
        if (!conflicts.isEmpty()) {
            throw new AppException(ErrorCode.CONFLICT,
                    "The requested time slot conflicts with an existing booking.");
        }

        // 5. Build and persist the booking.
        AmenityBooking booking = new AmenityBooking();
        booking.setAmenity(amenity);
        booking.setResident(resident);
        booking.setApartment(apartment);
        booking.setBookingDate(req.getBookingDate());
        booking.setStartTime(req.getStartTime());
        booking.setEndTime(req.getEndTime());
        booking.setNotes(req.getNotes());

        // Auto-approve when amenity does not require explicit admin approval.
        if (!amenity.isRequiresApproval()) {
            booking.setStatus(BookingStatus.APPROVED);
        } else {
            booking.setStatus(BookingStatus.PENDING);
        }

        AmenityBooking saved = bookingRepository.save(booking);
        log.info("Booking created — id={}, amenity={}, status={}", saved.getId(),
                amenity.getId(), saved.getStatus());
        return toBookingResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>When role is RESIDENT, ignores any client-supplied {@code residentId} and scopes results
     * to the caller's own active resident record (IDOR prevention). ADMIN and staff roles receive
     * the full unscoped list, optionally filtered by the supplied {@code residentId}.
     */
    @Override
    public PageResponse<AmenityBookingResponse> listBookings(UUID amenityId, UUID residentId,
                                                              BookingStatus status, Pageable pageable,
                                                              UUID principalId, String role) {
        log.debug("listBookings — amenityId={}, residentId={}, status={}, role={}", amenityId, residentId, status, role);

        // For RESIDENT callers, override residentId with their own resident record regardless of
        // what was supplied in the request — prevents IDOR by ensuring server-side scoping.
        final UUID effectiveResidentId;
        if ("RESIDENT".equals(role)) {
            Resident resident = residentRepository.findActiveByUserId(principalId)
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                            "No active resident record found for authenticated user."));
            effectiveResidentId = resident.getId();
        } else {
            effectiveResidentId = residentId;
        }

        Specification<AmenityBooking> spec = Specification.where(null);

        if (amenityId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("amenity").get("id"), amenityId));
        }
        if (effectiveResidentId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("resident").get("id"), effectiveResidentId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }

        Page<AmenityBookingResponse> page = bookingRepository.findAll(spec, pageable)
                .map(this::toBookingResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     *
     * <p>RESIDENT callers may only retrieve their own booking.
     * ADMIN and staff roles may retrieve any booking.
     */
    @Override
    public AmenityBookingResponse getBooking(UUID id, UUID principalId, String role) {
        log.debug("getBooking — id={}, role={}", id, role);

        AmenityBooking booking = requireBooking(id);

        // RESIDENT ownership check: the booking's resident user ID must match the principal.
        if ("RESIDENT".equals(role)) {
            UUID bookingOwnerUserId = booking.getResident().getUser().getId();
            if (!bookingOwnerUserId.equals(principalId)) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Access denied to booking: " + id);
            }
        }

        return toBookingResponse(booking);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only PENDING bookings may be approved or rejected.
     * Sets {@code approvedBy} and {@code approvedAt} on approval;
     * sets {@code rejectionReason} on rejection.
     */
    @Override
    @Transactional
    public AmenityBookingResponse approveOrReject(UUID id, ApproveRejectRequest req, UUID principalId) {
        log.debug("approveOrReject — id={}, targetStatus={}", id, req.getStatus());

        // Validate the requested target status is one of the two allowed values.
        if (req.getStatus() != BookingStatus.APPROVED && req.getStatus() != BookingStatus.REJECTED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "status must be APPROVED or REJECTED.");
        }

        AmenityBooking booking = requireBooking(id);

        // Only PENDING bookings may be acted upon.
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new AppException(ErrorCode.BOOKING_NOT_PENDING,
                    "Only PENDING bookings can be approved or rejected. Current status: "
                            + booking.getStatus());
        }

        User approver = userRepository.findById(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "User not found: " + principalId));

        booking.setStatus(req.getStatus());
        booking.setApprovedBy(approver);
        booking.setApprovedAt(OffsetDateTime.now());

        if (req.getStatus() == BookingStatus.REJECTED) {
            booking.setRejectionReason(req.getRejectionReason());
        }

        AmenityBooking saved = bookingRepository.save(booking);
        log.info("Booking {} — id={}, by admin={}", req.getStatus(), saved.getId(), principalId);
        return toBookingResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>RESIDENT may cancel only their own PENDING or APPROVED bookings where
     * the booking date is strictly in the future (after today).
     * ADMIN may cancel any non-terminal booking.
     */
    @Override
    @Transactional
    public AmenityBookingResponse cancelBooking(UUID id, UUID principalId, String role) {
        log.debug("cancelBooking — id={}, role={}", id, role);

        AmenityBooking booking = requireBooking(id);

        if ("RESIDENT".equals(role)) {
            // Ownership check.
            UUID bookingOwnerUserId = booking.getResident().getUser().getId();
            if (!bookingOwnerUserId.equals(principalId)) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Access denied to booking: " + id);
            }
            // Residents may only cancel PENDING or APPROVED bookings before the booking date.
            if (booking.getStatus() != BookingStatus.PENDING
                    && booking.getStatus() != BookingStatus.APPROVED) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Cannot cancel a booking with status: " + booking.getStatus());
            }
            if (!booking.getBookingDate().isAfter(LocalDate.now())) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Cannot cancel a booking on or before today's date.");
            }
        } else {
            // ADMIN may cancel any non-terminal booking.
            if (booking.getStatus() == BookingStatus.CANCELLED
                    || booking.getStatus() == BookingStatus.COMPLETED
                    || booking.getStatus() == BookingStatus.REJECTED) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Cannot cancel a booking with terminal status: " + booking.getStatus());
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        AmenityBooking saved = bookingRepository.save(booking);
        log.info("Booking cancelled — id={}, by principalId={}", saved.getId(), principalId);
        return toBookingResponse(saved);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Loads an amenity by ID or throws NOT_FOUND.
     *
     * @param id the amenity UUID.
     * @return the loaded amenity entity.
     */
    private Amenity requireAmenity(UUID id) {
        return amenityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Amenity not found: " + id));
    }

    /**
     * Loads an amenity booking by ID or throws NOT_FOUND.
     *
     * @param id the booking UUID.
     * @return the loaded booking entity.
     */
    private AmenityBooking requireBooking(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Booking not found: " + id));
    }

    /**
     * Maps an {@link Amenity} entity to an {@link AmenityResponse} DTO.
     *
     * @param amenity the entity to map.
     * @return the response DTO.
     */
    private AmenityResponse toAmenityResponse(Amenity amenity) {
        return AmenityResponse.builder()
                .id(amenity.getId())
                .name(amenity.getName())
                .description(amenity.getDescription())
                .location(amenity.getLocation())
                .capacity(amenity.getCapacity())
                .openingTime(amenity.getOpeningTime())
                .closingTime(amenity.getClosingTime())
                .maxDailyBookingsPerResident(amenity.getMaxDailyBookingsPerResident())
                .requiresApproval(amenity.isRequiresApproval())
                .active(amenity.isActive())
                .createdAt(amenity.getCreatedAt())
                .build();
    }

    /**
     * Maps an {@link AmenityBooking} entity to an {@link AmenityBookingResponse} DTO.
     *
     * <p>Lazily-loaded associations ({@code amenity}, {@code resident}, {@code apartment},
     * {@code approvedBy}) are accessed here; this method must be called within an active
     * transaction or with a loaded entity graph.
     *
     * @param booking the entity to map.
     * @return the response DTO.
     */
    private AmenityBookingResponse toBookingResponse(AmenityBooking booking) {
        AmenityBookingResponse.AmenityRef amenityRef = AmenityBookingResponse.AmenityRef.builder()
                .id(booking.getAmenity().getId())
                .name(booking.getAmenity().getName())
                .build();

        AmenityBookingResponse.ResidentRef.UserRef userRef =
                AmenityBookingResponse.ResidentRef.UserRef.builder()
                        .id(booking.getResident().getUser().getId())
                        .fullName(booking.getResident().getUser().getFullName())
                        .build();

        AmenityBookingResponse.ResidentRef residentRef = AmenityBookingResponse.ResidentRef.builder()
                .id(booking.getResident().getId())
                .user(userRef)
                .build();

        AmenityBookingResponse.ApartmentRef apartmentRef = AmenityBookingResponse.ApartmentRef.builder()
                .id(booking.getApartment().getId())
                .unitNumber(booking.getApartment().getUnitNumber())
                .build();

        AmenityBookingResponse.ApproverRef approverRef = null;
        if (booking.getApprovedBy() != null) {
            approverRef = AmenityBookingResponse.ApproverRef.builder()
                    .id(booking.getApprovedBy().getId())
                    .fullName(booking.getApprovedBy().getFullName())
                    .build();
        }

        return AmenityBookingResponse.builder()
                .id(booking.getId())
                .amenity(amenityRef)
                .resident(residentRef)
                .apartment(apartmentRef)
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(booking.getStatus())
                .notes(booking.getNotes())
                .rejectionReason(booking.getRejectionReason())
                .approvedBy(approverRef)
                .approvedAt(booking.getApprovedAt())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
