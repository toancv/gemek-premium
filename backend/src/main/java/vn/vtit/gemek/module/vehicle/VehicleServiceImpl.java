/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle;

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
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.vehicle.dto.CreateVehicleRequest;
import vn.vtit.gemek.module.vehicle.dto.UpdateVehicleRequest;
import vn.vtit.gemek.module.vehicle.dto.VehicleResponse;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;
import vn.vtit.gemek.module.vehicle.mapper.VehicleMapper;
import vn.vtit.gemek.module.vehicle.repository.VehicleRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link VehicleService}.
 *
 * <p>Soft deletion is used: the {@code isActive} flag is set to {@code false}
 * rather than physically removing the record. All write operations are
 * transactional; reads run in {@code readOnly} transactions.
 */
@Service
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final ResidentRepository residentRepository;
    private final ApartmentRepository apartmentRepository;
    private final VehicleMapper vehicleMapper;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param vehicleRepository   the vehicle JPA repository.
     * @param residentRepository  the resident JPA repository.
     * @param apartmentRepository the apartment JPA repository.
     * @param vehicleMapper       the MapStruct vehicle mapper.
     */
    public VehicleServiceImpl(VehicleRepository vehicleRepository,
                              ResidentRepository residentRepository,
                              ApartmentRepository apartmentRepository,
                              VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.residentRepository = residentRepository;
        this.apartmentRepository = apartmentRepository;
        this.vehicleMapper = vehicleMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<VehicleResponse> listVehicles(UUID apartmentId, Pageable pageable) {
        log.debug("Listing vehicles — apartmentId={}", apartmentId);

        Specification<Vehicle> spec = buildSpecification(apartmentId);
        Page<VehicleResponse> page = vehicleRepository.findAll(spec, pageable)
                .map(vehicleMapper::toResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest req, UUID principalId, String role) {
        log.debug("Creating vehicle — residentId={}, licensePlate={}", req.getResidentId(), req.getLicensePlate());

        Resident resident = residentRepository.findById(req.getResidentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Resident not found: " + req.getResidentId()));

        Apartment apartment = apartmentRepository.findById(req.getApartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Apartment not found: " + req.getApartmentId()));

        // RESIDENT callers may only register vehicles for their own active apartment.
        if ("RESIDENT".equals(role)) {
            verifyResidentOwnsApartment(principalId, req.getApartmentId());
        }

        // License plate must be globally unique.
        if (vehicleRepository.existsByLicensePlate(req.getLicensePlate())) {
            throw new AppException(ErrorCode.CONFLICT,
                    "License plate '" + req.getLicensePlate() + "' is already registered.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Vehicle vehicle = new Vehicle();
        vehicle.setResident(resident);
        vehicle.setApartment(apartment);
        vehicle.setType(req.getType());
        vehicle.setLicensePlate(req.getLicensePlate());
        vehicle.setBrand(req.getBrand());
        vehicle.setModel(req.getModel());
        vehicle.setColor(req.getColor());
        vehicle.setNotes(req.getNotes());
        vehicle.setActive(true);
        vehicle.setCreatedAt(now);
        vehicle.setUpdatedAt(now);

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle created — id={}, licensePlate={}", saved.getId(), saved.getLicensePlate());
        return vehicleMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VehicleResponse getVehicle(UUID id, UUID principalId, String role) {
        log.debug("Getting vehicle id={}", id);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Vehicle not found: " + id));

        // RESIDENT callers may only view vehicles registered to their own apartment.
        if ("RESIDENT".equals(role)) {
            verifyResidentOwnsApartment(principalId, vehicle.getApartment().getId());
        }

        return vehicleMapper.toResponse(vehicle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public VehicleResponse updateVehicle(UUID id, UpdateVehicleRequest req, UUID principalId, String role) {
        log.debug("Updating vehicle id={}", id);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Vehicle not found: " + id));

        // RESIDENT callers may only update vehicles belonging to their own apartment.
        if ("RESIDENT".equals(role)) {
            verifyResidentOwnsApartment(principalId, vehicle.getApartment().getId());
        }

        // License plate conflict check — exclude the current vehicle record.
        if (req.getLicensePlate() != null
                && vehicleRepository.existsByLicensePlateAndIdNot(req.getLicensePlate(), id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "License plate '" + req.getLicensePlate() + "' is already registered.");
        }

        if (req.getType() != null) {
            vehicle.setType(req.getType());
        }
        if (req.getLicensePlate() != null) {
            vehicle.setLicensePlate(req.getLicensePlate());
        }
        if (req.getBrand() != null) {
            vehicle.setBrand(req.getBrand());
        }
        if (req.getModel() != null) {
            vehicle.setModel(req.getModel());
        }
        if (req.getColor() != null) {
            vehicle.setColor(req.getColor());
        }
        if (req.getNotes() != null) {
            vehicle.setNotes(req.getNotes());
        }

        vehicle.setUpdatedAt(OffsetDateTime.now());
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle updated — id={}", saved.getId());
        return vehicleMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteVehicle(UUID id, UUID principalId, String role) {
        log.debug("Soft-deleting vehicle id={}", id);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Vehicle not found: " + id));

        // RESIDENT callers may only deactivate vehicles belonging to their own apartment.
        if ("RESIDENT".equals(role)) {
            verifyResidentOwnsApartment(principalId, vehicle.getApartment().getId());
        }

        vehicle.setActive(false);
        vehicle.setUpdatedAt(OffsetDateTime.now());
        vehicleRepository.save(vehicle);
        log.info("Vehicle soft-deleted — id={}", id);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a JPA {@link Specification} from optional filter parameters.
     *
     * @param apartmentId optional apartment UUID filter.
     * @return the composed specification.
     */
    private Specification<Vehicle> buildSpecification(UUID apartmentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (apartmentId != null) {
                predicates.add(cb.equal(root.get("apartment").get("id"), apartmentId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Verifies that the given principal is an active resident of the specified apartment.
     *
     * <p>Throws {@link AppException} with {@link ErrorCode#FORBIDDEN} when the check fails.
     *
     * @param principalId the authenticated user's UUID.
     * @param apartmentId the apartment UUID to verify ownership of.
     */
    private void verifyResidentOwnsApartment(UUID principalId, UUID apartmentId) {
        boolean owns = residentRepository.findActiveByUserId(principalId)
                .map(r -> r.getApartment().getId().equals(apartmentId))
                .orElse(false);
        if (!owns) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "You are not an active resident of this apartment.");
        }
    }
}
