/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.mapper.ApartmentMapper;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.vehicle.repository.VehicleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApartmentServiceImpl} — GAP-11 status transitions and access guards.
 *
 * <p>Covers: RESIDENT cross-apartment FORBIDDEN, NOT_FOUND guards, duplicate unit CONFLICT,
 * and delete-with-active-residents CONFLICT.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApartmentServiceImplTest {

    @Mock private ApartmentRepository apartmentRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ApartmentMapper apartmentMapper;

    private ApartmentServiceImpl service;

    private UUID apartmentId;
    private UUID blockId;
    private UUID userId;
    private Block block;
    private Apartment apartment;

    @BeforeEach
    void setUp() {
        service = new ApartmentServiceImpl(
                apartmentRepository, blockRepository,
                residentRepository, vehicleRepository, apartmentMapper);

        apartmentId = UUID.randomUUID();
        blockId = UUID.randomUUID();
        userId = UUID.randomUUID();

        block = new Block();
        block.setId(blockId);
        block.setName("Block A");

        apartment = new Apartment();
        apartment.setId(apartmentId);
        apartment.setBlock(block);
        apartment.setFloor((short) 3);
        apartment.setUnitNumber("A301");
    }

    // =========================================================================
    // getApartmentDetail — RESIDENT accessing another apartment → FORBIDDEN
    // =========================================================================

    @Test
    @DisplayName("getApartmentDetail — RESIDENT not in apartment throws FORBIDDEN")
    void getApartmentDetail_residentNotInApartment_throwsForbidden() {
        UUID differentUserId = UUID.randomUUID();
        when(apartmentRepository.findByIdWithBlock(apartmentId)).thenReturn(Optional.of(apartment));

        // Active resident list contains userId, but requester is differentUserId.
        User residentUser = new User();
        residentUser.setId(userId);
        Resident resident = new Resident();
        resident.setUser(residentUser);
        resident.setType(ResidentType.OWNER);
        when(residentRepository.findActiveByApartmentId(apartmentId)).thenReturn(List.of(resident));

        assertThatThrownBy(() -> service.getApartmentDetail(apartmentId, differentUserId, true))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // =========================================================================
    // getApartmentDetail — apartment not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("getApartmentDetail — unknown apartment ID throws NOT_FOUND")
    void getApartmentDetail_apartmentNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(apartmentRepository.findByIdWithBlock(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApartmentDetail(unknownId, userId, false))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // createApartment — block not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("createApartment — unknown block ID throws NOT_FOUND")
    void createApartment_blockNotFound_throwsNotFound() {
        UUID unknownBlockId = UUID.randomUUID();
        when(blockRepository.findById(unknownBlockId)).thenReturn(Optional.empty());

        CreateApartmentRequest request = new CreateApartmentRequest(
                unknownBlockId, (short) 3, "A301", null, null);

        assertThatThrownBy(() -> service.createApartment(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // createApartment — duplicate unit number in block → CONFLICT
    // =========================================================================

    @Test
    @DisplayName("createApartment — duplicate unit number in same block throws CONFLICT")
    void createApartment_duplicateUnitNumber_throwsConflict() {
        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(apartmentRepository.existsByBlockIdAndUnitNumber(blockId, "A301")).thenReturn(true);

        CreateApartmentRequest request = new CreateApartmentRequest(
                blockId, (short) 3, "A301", null, null);

        assertThatThrownBy(() -> service.createApartment(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    // =========================================================================
    // deleteApartment — apartment not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("deleteApartment — unknown apartment ID throws NOT_FOUND")
    void deleteApartment_apartmentNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(apartmentRepository.findByIdWithBlock(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteApartment(unknownId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // deleteApartment — has active residents → CONFLICT
    // =========================================================================

    @Test
    @DisplayName("deleteApartment — apartment with active residents throws CONFLICT")
    void deleteApartment_hasActiveResidents_throwsConflict() {
        when(apartmentRepository.findByIdWithBlock(apartmentId)).thenReturn(Optional.of(apartment));
        when(residentRepository.existsActiveByApartmentId(apartmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteApartment(apartmentId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }
}
