/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking;

import jakarta.persistence.criteria.Predicate;
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
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.parking.dto.CheckoutRequest;
import vn.vtit.gemek.module.parking.dto.CreateAssignmentRequest;
import vn.vtit.gemek.module.parking.dto.CreateGuestVehicleRequest;
import vn.vtit.gemek.module.parking.dto.CreateParkingSlotRequest;
import vn.vtit.gemek.module.parking.dto.GuestVehicleResponse;
import vn.vtit.gemek.module.parking.dto.ParkingAssignmentResponse;
import vn.vtit.gemek.module.parking.dto.ParkingSlotResponse;
import vn.vtit.gemek.module.parking.dto.UnassignRequest;
import vn.vtit.gemek.module.parking.dto.UpdateParkingSlotRequest;
import vn.vtit.gemek.module.parking.entity.GuestVehicle;
import vn.vtit.gemek.module.parking.entity.ParkingAssignment;
import vn.vtit.gemek.module.parking.entity.ParkingSlot;
import vn.vtit.gemek.module.parking.entity.ParkingSlotStatus;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;
import vn.vtit.gemek.module.parking.repository.GuestVehicleRepository;
import vn.vtit.gemek.module.parking.repository.ParkingAssignmentRepository;
import vn.vtit.gemek.module.parking.repository.ParkingSlotRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;
import vn.vtit.gemek.module.vehicle.repository.VehicleRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link ParkingService}.
 *
 * <p>All class-level reads run under a read-only transaction for performance.
 * Write operations override this with a full read-write transaction.
 * Slot status and assignment record are always updated atomically within the same transaction.
 */
@Service
@Transactional(readOnly = true)
public class ParkingServiceImpl implements ParkingService {

    private static final Logger log = LoggerFactory.getLogger(ParkingServiceImpl.class);

    private final ParkingSlotRepository slotRepository;
    private final ParkingAssignmentRepository assignmentRepository;
    private final GuestVehicleRepository guestVehicleRepository;
    private final VehicleRepository vehicleRepository;
    private final ApartmentRepository apartmentRepository;
    private final UserRepository userRepository;

    /**
     * Constructs the service with all required dependencies via constructor injection.
     *
     * @param slotRepository         the parking slot repository.
     * @param assignmentRepository   the parking assignment repository.
     * @param guestVehicleRepository the guest vehicle repository.
     * @param vehicleRepository      the vehicle repository.
     * @param apartmentRepository    the apartment repository.
     * @param userRepository         the user repository.
     */
    public ParkingServiceImpl(ParkingSlotRepository slotRepository,
                              ParkingAssignmentRepository assignmentRepository,
                              GuestVehicleRepository guestVehicleRepository,
                              VehicleRepository vehicleRepository,
                              ApartmentRepository apartmentRepository,
                              UserRepository userRepository) {
        this.slotRepository = slotRepository;
        this.assignmentRepository = assignmentRepository;
        this.guestVehicleRepository = guestVehicleRepository;
        this.vehicleRepository = vehicleRepository;
        this.apartmentRepository = apartmentRepository;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // Parking slots
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ParkingSlotResponse> listSlots(ParkingSlotType type,
                                                       ParkingSlotStatus status,
                                                       String zone,
                                                       Pageable pageable) {
        log.debug("Listing parking slots — type={}, status={}, zone={}", type, status, zone);
        Specification<ParkingSlot> spec = buildSlotSpecification(type, status, zone);
        Page<ParkingSlotResponse> page = slotRepository.findAll(spec, pageable)
                .map(this::toSlotResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ParkingSlotResponse createSlot(CreateParkingSlotRequest req) {
        log.debug("Creating parking slot — slotNumber={}", req.getSlotNumber());

        // Slot numbers must be globally unique.
        if (slotRepository.existsBySlotNumber(req.getSlotNumber())) {
            throw new AppException(ErrorCode.SLOT_NUMBER_ALREADY_EXISTS,
                    "Parking slot number is already in use.");
        }

        ParkingSlot slot = new ParkingSlot();
        slot.setSlotNumber(req.getSlotNumber());
        slot.setZone(req.getZone());
        slot.setType(req.getType());
        slot.setStatus(ParkingSlotStatus.AVAILABLE);
        slot.setNotes(req.getNotes());

        ParkingSlot saved = slotRepository.save(slot);
        log.info("Parking slot created — id={}, slotNumber={}", saved.getId(), saved.getSlotNumber());
        return toSlotResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParkingSlotResponse getSlot(UUID id) {
        log.debug("Getting parking slot id={}", id);
        return toSlotResponse(loadSlot(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ParkingSlotResponse updateSlot(UUID id, UpdateParkingSlotRequest req) {
        log.debug("Updating parking slot id={}", id);
        ParkingSlot slot = loadSlot(id);

        // Check slot number uniqueness only when the caller supplies a new value.
        if (req.getSlotNumber() != null
                && slotRepository.existsBySlotNumberAndIdNot(req.getSlotNumber(), id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Slot number '" + req.getSlotNumber() + "' already exists.");
        }

        if (req.getSlotNumber() != null) {
            slot.setSlotNumber(req.getSlotNumber());
        }
        if (req.getZone() != null) {
            slot.setZone(req.getZone());
        }
        if (req.getType() != null) {
            slot.setType(req.getType());
        }
        if (req.getStatus() != null) {
            slot.setStatus(req.getStatus());
        }
        if (req.getNotes() != null) {
            slot.setNotes(req.getNotes());
        }

        ParkingSlot saved = slotRepository.save(slot);
        log.info("Parking slot updated — id={}", saved.getId());
        return toSlotResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteSlot(UUID id) {
        log.debug("Deleting parking slot id={}", id);
        ParkingSlot slot = loadSlot(id);

        // Prevent deletion if any assignment (active or historical) references this slot.
        if (assignmentRepository.existsByParkingSlotId(id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Parking slot has existing assignments and cannot be deleted.");
        }

        slotRepository.delete(slot);
        log.info("Parking slot deleted — id={}", id);
    }

    // =========================================================================
    // Assignments
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ParkingAssignmentResponse assignSlot(UUID slotId,
                                                CreateAssignmentRequest req,
                                                UUID principalId) {
        log.debug("Assigning slot id={} to vehicle id={}", slotId, req.getVehicleId());

        ParkingSlot slot = loadSlot(slotId);

        // Only AVAILABLE slots may receive a new assignment.
        if (slot.getStatus() != ParkingSlotStatus.AVAILABLE) {
            throw new AppException(ErrorCode.CONFLICT, "Slot is not available.");
        }

        // Guard against a status/assignment state mismatch — check the assignment table directly.
        if (slotRepository.hasActiveAssignment(slotId)) {
            throw new AppException(ErrorCode.CONFLICT, "Slot already has an active assignment.");
        }

        Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Vehicle not found: " + req.getVehicleId()));

        Apartment apartment = apartmentRepository.findById(req.getApartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Apartment not found: " + req.getApartmentId()));

        ParkingAssignment assignment = new ParkingAssignment();
        assignment.setParkingSlot(slot);
        assignment.setVehicle(vehicle);
        assignment.setApartment(apartment);
        assignment.setStartDate(req.getStartDate());
        assignment.setEndDate(null);
        assignment.setParkingCardNumber(req.getParkingCardNumber());
        assignment.setNotes(req.getNotes());

        // Set slot OCCUPIED atomically within the same transaction.
        slot.setStatus(ParkingSlotStatus.OCCUPIED);
        slotRepository.save(slot);

        ParkingAssignment saved = assignmentRepository.save(assignment);
        log.info("Parking assignment created — id={}, slotId={}, vehicleId={}",
                saved.getId(), slotId, req.getVehicleId());
        return toAssignmentResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ParkingAssignmentResponse unassignSlot(UUID slotId, UnassignRequest req) {
        log.debug("Unassigning slot id={}", slotId);

        ParkingSlot slot = loadSlot(slotId);

        ParkingAssignment assignment = assignmentRepository.findActiveBySlotId(slotId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "No active assignment found for slot: " + slotId));

        // Default end date to today when not supplied.
        LocalDate endDate = (req.getEndDate() != null) ? req.getEndDate() : LocalDate.now();
        assignment.setEndDate(endDate);

        // Set slot AVAILABLE atomically within the same transaction.
        slot.setStatus(ParkingSlotStatus.AVAILABLE);
        slotRepository.save(slot);

        ParkingAssignment saved = assignmentRepository.save(assignment);
        log.info("Parking assignment ended — id={}, slotId={}, endDate={}", saved.getId(), slotId, endDate);
        return toAssignmentResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ParkingAssignmentResponse> listAssignments(UUID apartmentId,
                                                                   UUID slotId,
                                                                   Boolean active,
                                                                   Pageable pageable) {
        log.debug("Listing assignments — apartmentId={}, slotId={}, active={}", apartmentId, slotId, active);
        Specification<ParkingAssignment> spec = buildAssignmentSpecification(apartmentId, slotId, active);
        Page<ParkingAssignmentResponse> page = assignmentRepository.findAll(spec, pageable)
                .map(this::toAssignmentResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParkingAssignmentResponse getAssignment(UUID id) {
        log.debug("Getting assignment id={}", id);
        ParkingAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Assignment not found: " + id));
        return toAssignmentResponse(assignment);
    }

    // =========================================================================
    // Guest vehicles
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<GuestVehicleResponse> listGuestVehicles(UUID apartmentId,
                                                                Boolean active,
                                                                Pageable pageable) {
        log.debug("Listing guest vehicles — apartmentId={}, active={}", apartmentId, active);
        Specification<GuestVehicle> spec = buildGuestSpecification(apartmentId, active);
        Page<GuestVehicleResponse> page = guestVehicleRepository.findAll(spec, pageable)
                .map(this::toGuestResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public GuestVehicleResponse logGuestVehicle(CreateGuestVehicleRequest req, UUID principalId) {
        log.debug("Logging guest vehicle — licensePlate={}, apartmentId={}",
                req.getLicensePlate(), req.getHostApartmentId());

        Apartment apartment = apartmentRepository.findById(req.getHostApartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Apartment not found: " + req.getHostApartmentId()));

        // Load the recording user — optional FK, null is acceptable if user is absent.
        User recordedBy = userRepository.findById(principalId).orElse(null);

        GuestVehicle guest = new GuestVehicle();
        guest.setLicensePlate(req.getLicensePlate());
        guest.setOwnerName(req.getOwnerName());
        guest.setHostApartment(apartment);
        guest.setPurpose(req.getPurpose());
        guest.setRecordedBy(recordedBy);
        guest.setNotes(req.getNotes());
        // entryTime is defaulted by @PrePersist when null.

        GuestVehicle saved = guestVehicleRepository.save(guest);
        log.info("Guest vehicle logged — id={}, licensePlate={}", saved.getId(), saved.getLicensePlate());
        return toGuestResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public GuestVehicleResponse checkoutGuest(UUID id, CheckoutRequest req) {
        log.debug("Checking out guest vehicle id={}", id);

        GuestVehicle guest = guestVehicleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Guest vehicle not found: " + id));

        // Prevent double checkout.
        if (guest.getExitTime() != null) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Guest vehicle has already been checked out.");
        }

        OffsetDateTime exitTime = (req.getExitTime() != null) ? req.getExitTime() : OffsetDateTime.now();
        guest.setExitTime(exitTime);

        GuestVehicle saved = guestVehicleRepository.save(guest);
        log.info("Guest vehicle checked out — id={}, exitTime={}", saved.getId(), exitTime);
        return toGuestResponse(saved);
    }

    // =========================================================================
    // Private helpers — entity loaders
    // =========================================================================

    /**
     * Loads a {@link ParkingSlot} by ID or throws {@link AppException} with NOT_FOUND.
     *
     * @param id the slot UUID.
     * @return the parking slot entity.
     */
    private ParkingSlot loadSlot(UUID id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Parking slot not found: " + id));
    }

    // =========================================================================
    // Private helpers — specifications
    // =========================================================================

    /**
     * Builds a JPA {@link Specification} for parking slot list filtering.
     *
     * @param type   optional type filter.
     * @param status optional status filter.
     * @param zone   optional zone substring filter.
     * @return composed specification.
     */
    private Specification<ParkingSlot> buildSlotSpecification(ParkingSlotType type,
                                                               ParkingSlotStatus status,
                                                               String zone) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (zone != null && !zone.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("zone")),
                        "%" + zone.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Builds a JPA {@link Specification} for assignment list filtering.
     *
     * @param apartmentId optional apartment UUID filter.
     * @param slotId      optional slot UUID filter.
     * @param active      optional active-only flag.
     * @return composed specification.
     */
    private Specification<ParkingAssignment> buildAssignmentSpecification(UUID apartmentId,
                                                                           UUID slotId,
                                                                           Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (apartmentId != null) {
                predicates.add(cb.equal(root.get("apartment").get("id"), apartmentId));
            }
            if (slotId != null) {
                predicates.add(cb.equal(root.get("parkingSlot").get("id"), slotId));
            }
            if (Boolean.TRUE.equals(active)) {
                predicates.add(cb.isNull(root.get("endDate")));
            } else if (Boolean.FALSE.equals(active)) {
                predicates.add(cb.isNotNull(root.get("endDate")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Builds a JPA {@link Specification} for guest vehicle list filtering.
     *
     * @param apartmentId optional host apartment UUID filter.
     * @param active      optional active-only flag ({@code true} = exit_time IS NULL).
     * @return composed specification.
     */
    private Specification<GuestVehicle> buildGuestSpecification(UUID apartmentId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (apartmentId != null) {
                predicates.add(cb.equal(root.get("hostApartment").get("id"), apartmentId));
            }
            if (Boolean.TRUE.equals(active)) {
                predicates.add(cb.isNull(root.get("exitTime")));
            } else if (Boolean.FALSE.equals(active)) {
                predicates.add(cb.isNotNull(root.get("exitTime")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // =========================================================================
    // Private helpers — mapping
    // =========================================================================

    /**
     * Maps a {@link ParkingSlot} entity to a {@link ParkingSlotResponse} DTO.
     *
     * @param slot the entity to map.
     * @return the response DTO.
     */
    private ParkingSlotResponse toSlotResponse(ParkingSlot slot) {
        return ParkingSlotResponse.builder()
                .id(slot.getId())
                .slotNumber(slot.getSlotNumber())
                .zone(slot.getZone())
                .type(slot.getType())
                .status(slot.getStatus())
                .notes(slot.getNotes())
                .createdAt(slot.getCreatedAt())
                .build();
    }

    /**
     * Maps a {@link ParkingAssignment} entity to a {@link ParkingAssignmentResponse} DTO.
     *
     * @param a the entity to map.
     * @return the response DTO.
     */
    private ParkingAssignmentResponse toAssignmentResponse(ParkingAssignment a) {
        ParkingSlot slot = a.getParkingSlot();
        Vehicle vehicle = a.getVehicle();
        Apartment apartment = a.getApartment();

        return ParkingAssignmentResponse.builder()
                .id(a.getId())
                .slot(ParkingAssignmentResponse.SlotRef.builder()
                        .id(slot.getId())
                        .slotNumber(slot.getSlotNumber())
                        .zone(slot.getZone())
                        .type(slot.getType())
                        .build())
                .vehicle(ParkingAssignmentResponse.VehicleRef.builder()
                        .id(vehicle.getId())
                        .licensePlate(vehicle.getLicensePlate())
                        .type(vehicle.getType())
                        .build())
                .apartment(ParkingAssignmentResponse.ApartmentRef.builder()
                        .id(apartment.getId())
                        .unitNumber(apartment.getUnitNumber())
                        .build())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .parkingCardNumber(a.getParkingCardNumber())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }

    /**
     * Maps a {@link GuestVehicle} entity to a {@link GuestVehicleResponse} DTO.
     *
     * @param g the entity to map.
     * @return the response DTO.
     */
    private GuestVehicleResponse toGuestResponse(GuestVehicle g) {
        GuestVehicleResponse.UserRef recordedByRef = null;
        if (g.getRecordedBy() != null) {
            recordedByRef = GuestVehicleResponse.UserRef.builder()
                    .id(g.getRecordedBy().getId())
                    .fullName(g.getRecordedBy().getFullName())
                    .build();
        }

        return GuestVehicleResponse.builder()
                .id(g.getId())
                .licensePlate(g.getLicensePlate())
                .ownerName(g.getOwnerName())
                .hostApartment(GuestVehicleResponse.ApartmentRef.builder()
                        .id(g.getHostApartment().getId())
                        .unitNumber(g.getHostApartment().getUnitNumber())
                        .build())
                .entryTime(g.getEntryTime())
                .exitTime(g.getExitTime())
                .purpose(g.getPurpose())
                .recordedBy(recordedByRef)
                .notes(g.getNotes())
                .build();
    }
}
