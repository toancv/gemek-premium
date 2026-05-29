/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.apartment.dto.ApartmentDetailResponse;
import vn.vtit.gemek.module.apartment.dto.ApartmentSummaryResponse;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.UpdateApartmentRequest;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.mapper.ApartmentMapper;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;
import vn.vtit.gemek.module.vehicle.repository.VehicleRepository;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link ApartmentService} for apartment management.
 *
 * <p>The list endpoint fetches primary contacts via a separate query per page item;
 * this is acceptable at the stated scale (~1000 apartments). If this becomes a
 * performance concern, a single JOIN query with a subselect can replace it.
 *
 * <p>The detail endpoint fetches residents and vehicles in two separate queries
 * to avoid a Cartesian product join.
 */
@Service
@Transactional(readOnly = true)
public class ApartmentServiceImpl implements ApartmentService {

    private static final Logger log = LoggerFactory.getLogger(ApartmentServiceImpl.class);

    private final ApartmentRepository apartmentRepository;
    private final BlockRepository blockRepository;
    private final ResidentRepository residentRepository;
    private final VehicleRepository vehicleRepository;
    private final ApartmentMapper apartmentMapper;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param apartmentRepository the apartment JPA repository.
     * @param blockRepository     the block JPA repository.
     * @param residentRepository  the resident JPA repository (stub for Module 3).
     * @param vehicleRepository   the vehicle JPA repository (stub for Module 3).
     * @param apartmentMapper     the MapStruct apartment mapper.
     */
    public ApartmentServiceImpl(ApartmentRepository apartmentRepository,
                                BlockRepository blockRepository,
                                ResidentRepository residentRepository,
                                VehicleRepository vehicleRepository,
                                ApartmentMapper apartmentMapper) {
        this.apartmentRepository = apartmentRepository;
        this.blockRepository = blockRepository;
        this.residentRepository = residentRepository;
        this.vehicleRepository = vehicleRepository;
        this.apartmentMapper = apartmentMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ApartmentSummaryResponse> listApartments(
            UUID blockId, Short floor, ApartmentStatus status, String search, Pageable pageable) {
        log.debug("Listing apartments — blockId={}, floor={}, status={}, search={}", blockId, floor, status, search);

        Page<ApartmentSummaryResponse> page = apartmentRepository
                .findAllWithFilters(blockId, floor, status, search, pageable)
                .map(apartment -> {
                    // Build the summary without primary contact first.
                    ApartmentSummaryResponse base = apartmentMapper.toSummaryResponse(apartment);

                    // Resolve primary contact via a targeted query on the residents table.
                    ApartmentSummaryResponse.PrimaryContactRef contact = residentRepository
                            .findActiveByApartmentId(apartment.getId())
                            .stream()
                            .filter(Resident::isPrimaryContact)
                            .findFirst()
                            .map(r -> new ApartmentSummaryResponse.PrimaryContactRef(
                                    r.getId(),
                                    r.getUser().getFullName(),
                                    r.getType(),
                                    r.getUser().getPhone()))
                            .orElse(null);

                    // Rebuild the record with the primary contact field populated.
                    return new ApartmentSummaryResponse(
                            base.id(),
                            base.block(),
                            base.floor(),
                            base.unitNumber(),
                            base.areaSqm(),
                            base.status(),
                            contact);
                });

        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ApartmentSummaryResponse createApartment(CreateApartmentRequest request) {
        log.debug("Creating apartment — blockId={}, unitNumber={}", request.blockId(), request.unitNumber());

        Block block = blockRepository.findById(request.blockId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Block not found: " + request.blockId()));

        // Unit number must be unique within the block.
        if (apartmentRepository.existsByBlockIdAndUnitNumber(request.blockId(), request.unitNumber())) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Unit number '" + request.unitNumber() + "' already exists in this block.");
        }

        Apartment apartment = new Apartment();
        apartment.setBlock(block);
        apartment.setFloor(request.floor());
        apartment.setUnitNumber(request.unitNumber());
        apartment.setAreaSqm(request.areaSqm());
        apartment.setStatus(ApartmentStatus.AVAILABLE);
        apartment.setNotes(request.notes());

        Apartment saved = apartmentRepository.save(apartment);
        log.info("Apartment created — id={}, unitNumber={}", saved.getId(), saved.getUnitNumber());
        return apartmentMapper.toSummaryResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApartmentDetailResponse getApartmentDetail(UUID id, UUID requestingUserId, boolean isResident) {
        log.debug("Getting apartment detail — id={}, requestingUserId={}, isResident={}", id, requestingUserId, isResident);

        Apartment apartment = apartmentRepository.findByIdWithBlock(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Apartment not found: " + id));

        // RESIDENT role: verify the requesting user is an active resident of this apartment.
        if (isResident) {
            boolean isOwnApartment = residentRepository.findActiveByApartmentId(id)
                    .stream()
                    .anyMatch(r -> r.getUser().getId().equals(requestingUserId));
            if (!isOwnApartment) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "You are not a resident of this apartment.");
            }
        }

        // Fetch active residents and all vehicles in two separate queries.
        List<Resident> residents = residentRepository.findActiveByApartmentId(id);
        List<Vehicle> vehicles = vehicleRepository.findByApartmentId(id);

        ApartmentDetailResponse base = apartmentMapper.toDetailResponse(apartment);

        List<ApartmentDetailResponse.ResidentRef> residentRefs = residents.stream()
                .map(apartmentMapper::toResidentRef)
                .toList();
        List<ApartmentDetailResponse.VehicleRef> vehicleRefs = vehicles.stream()
                .map(apartmentMapper::toVehicleRef)
                .toList();

        // Reconstruct the record with the populated lists.
        return new ApartmentDetailResponse(
                base.id(),
                base.block(),
                base.floor(),
                base.unitNumber(),
                base.areaSqm(),
                base.status(),
                base.notes(),
                residentRefs,
                vehicleRefs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ApartmentSummaryResponse updateApartment(UUID id, UpdateApartmentRequest request) {
        log.debug("Updating apartment id={}", id);

        Apartment apartment = apartmentRepository.findByIdWithBlock(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Apartment not found: " + id));

        // Unit number conflict check — exclude the apartment being updated.
        if (apartmentRepository.existsByBlockIdAndUnitNumberAndIdNot(
                apartment.getBlock().getId(), request.unitNumber(), id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Unit number '" + request.unitNumber() + "' already exists in this block.");
        }

        apartment.setFloor(request.floor());
        apartment.setUnitNumber(request.unitNumber());
        apartment.setAreaSqm(request.areaSqm());
        apartment.setStatus(request.status());
        apartment.setNotes(request.notes());

        Apartment saved = apartmentRepository.save(apartment);
        log.info("Apartment updated — id={}", saved.getId());
        return apartmentMapper.toSummaryResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteApartment(UUID id) {
        log.debug("Deleting apartment id={}", id);

        Apartment apartment = apartmentRepository.findByIdWithBlock(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Apartment not found: " + id));

        // Prevent deletion when active residents exist.
        if (residentRepository.existsActiveByApartmentId(id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Apartment '" + apartment.getUnitNumber() + "' has active residents and cannot be deleted.");
        }

        apartmentRepository.delete(apartment);
        log.info("Apartment deleted — id={}", id);
    }
}
