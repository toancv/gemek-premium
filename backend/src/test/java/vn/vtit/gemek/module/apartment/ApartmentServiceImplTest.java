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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.vehicle.repository.VehicleRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    // =========================================================================
    // listApartments — occupancy is DERIVED, MAINTENANCE priority, no N+1
    // =========================================================================

    @Test
    @DisplayName("listApartments — status derived (OCCUPIED/AVAILABLE/MAINTENANCE) in one batch query, no N+1")
    void listApartments_derivesStatus_batchNoNPlus1() {
        // apt1 AVAILABLE + active resident → OCCUPIED; apt2 AVAILABLE + none → AVAILABLE;
        // apt3 MAINTENANCE + active resident → MAINTENANCE (priority).
        Apartment apt1 = apartmentWith(ApartmentStatus.AVAILABLE, "A101");
        Apartment apt2 = apartmentWith(ApartmentStatus.AVAILABLE, "A102");
        Apartment apt3 = apartmentWith(ApartmentStatus.MAINTENANCE, "A103");

        Pageable pageable = PageRequest.of(0, 20);
        Page<Apartment> page = new PageImpl<>(List.of(apt1, apt2, apt3), pageable, 3);
        when(apartmentRepository.findAllWithFilters(null, null, null, null, pageable)).thenReturn(page);

        // Single batch query returns active residents for apt1 and apt3 only.
        when(residentRepository.findActiveByApartmentIdIn(any()))
                .thenReturn(List.of(activeResident(apt1), activeResident(apt3)));

        when(apartmentMapper.toSummaryResponse(apt1)).thenReturn(summaryFor(apt1));
        when(apartmentMapper.toSummaryResponse(apt2)).thenReturn(summaryFor(apt2));
        when(apartmentMapper.toSummaryResponse(apt3)).thenReturn(summaryFor(apt3));

        PageResponse<ApartmentSummaryResponse> result =
                service.listApartments(null, null, null, null, pageable);

        assertThat(result.getData()).extracting(ApartmentSummaryResponse::status)
                .containsExactly(ApartmentStatus.OCCUPIED, ApartmentStatus.AVAILABLE, ApartmentStatus.MAINTENANCE);

        // N+1 guard: occupancy resolved via ONE batch call; the per-apartment query is never used.
        verify(residentRepository, times(1)).findActiveByApartmentIdIn(any());
        verify(residentRepository, never()).findActiveByApartmentId(any());
    }

    // =========================================================================
    // getApartmentDetail — occupancy derived from active residents
    // =========================================================================

    @Test
    @DisplayName("getApartmentDetail — active resident derives OCCUPIED")
    void getApartmentDetail_activeResident_occupied() {
        apartment.setStatus(ApartmentStatus.AVAILABLE);
        when(apartmentRepository.findByIdWithBlock(apartmentId)).thenReturn(Optional.of(apartment));
        when(residentRepository.findActiveByApartmentId(apartmentId))
                .thenReturn(List.of(activeResident(apartment)));
        when(vehicleRepository.findByApartmentId(apartmentId)).thenReturn(List.of());
        when(apartmentMapper.toDetailResponse(apartment)).thenReturn(detailFor(apartment));

        ApartmentDetailResponse result = service.getApartmentDetail(apartmentId, userId, false);

        assertThat(result.status()).isEqualTo(ApartmentStatus.OCCUPIED);
    }

    @Test
    @DisplayName("getApartmentDetail — MAINTENANCE wins over active resident")
    void getApartmentDetail_maintenanceWithResident_maintenance() {
        apartment.setStatus(ApartmentStatus.MAINTENANCE);
        when(apartmentRepository.findByIdWithBlock(apartmentId)).thenReturn(Optional.of(apartment));
        when(residentRepository.findActiveByApartmentId(apartmentId))
                .thenReturn(List.of(activeResident(apartment)));
        when(vehicleRepository.findByApartmentId(apartmentId)).thenReturn(List.of());
        when(apartmentMapper.toDetailResponse(apartment)).thenReturn(detailFor(apartment));

        ApartmentDetailResponse result = service.getApartmentDetail(apartmentId, userId, false);

        assertThat(result.status()).isEqualTo(ApartmentStatus.MAINTENANCE);
    }

    // =========================================================================
    // updateApartment — occupancy is fully derived; status is NOT client-settable
    // =========================================================================

    @Test
    @DisplayName("updateApartment — stored status is preserved (not settable via update)")
    void updateApartment_doesNotChangeStoredStatus() {
        // Stored status is MAINTENANCE; the update request carries no status field at all.
        apartment.setStatus(ApartmentStatus.MAINTENANCE);
        when(apartmentRepository.findByIdWithBlock(apartmentId)).thenReturn(Optional.of(apartment));
        when(apartmentRepository.existsByBlockIdAndUnitNumberAndIdNot(blockId, "A999", apartmentId))
                .thenReturn(false);
        when(apartmentRepository.save(any(Apartment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(apartmentMapper.toSummaryResponse(any(Apartment.class))).thenReturn(summaryFor(apartment));

        UpdateApartmentRequest request =
                new UpdateApartmentRequest((short) 9, "A999", new BigDecimal("80.5"), "updated notes");
        service.updateApartment(apartmentId, request);

        // The persisted entity keeps its stored status — the desync hole is closed.
        ArgumentCaptor<Apartment> saved = ArgumentCaptor.forClass(Apartment.class);
        verify(apartmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ApartmentStatus.MAINTENANCE);

        // Other editable fields are still applied.
        assertThat(saved.getValue().getFloor()).isEqualTo((short) 9);
        assertThat(saved.getValue().getUnitNumber()).isEqualTo("A999");
        assertThat(saved.getValue().getAreaSqm()).isEqualByComparingTo("80.5");
        assertThat(saved.getValue().getNotes()).isEqualTo("updated notes");
    }

    // ── test fixtures ───────────────────────────────────────────────────────

    private Apartment apartmentWith(ApartmentStatus status, String unitNumber) {
        Apartment apt = new Apartment();
        apt.setId(UUID.randomUUID());
        apt.setBlock(block);
        apt.setFloor((short) 1);
        apt.setUnitNumber(unitNumber);
        apt.setStatus(status);
        return apt;
    }

    private Resident activeResident(Apartment apt) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Resident " + apt.getUnitNumber());
        Resident r = new Resident();
        r.setId(UUID.randomUUID());
        r.setApartment(apt);
        r.setUser(user);
        r.setType(ResidentType.OWNER);
        r.setPrimaryContact(false);
        return r;
    }

    private ApartmentSummaryResponse summaryFor(Apartment apt) {
        return new ApartmentSummaryResponse(
                apt.getId(),
                new ApartmentSummaryResponse.BlockRef(blockId, "Block A"),
                apt.getFloor(),
                apt.getUnitNumber(),
                null,
                apt.getStatus(),
                null);
    }

    private ApartmentDetailResponse detailFor(Apartment apt) {
        return new ApartmentDetailResponse(
                apt.getId(),
                new ApartmentDetailResponse.BlockRef(blockId, "Block A"),
                apt.getFloor(),
                apt.getUnitNumber(),
                null,
                apt.getStatus(),
                null,
                List.of(),
                List.of());
    }
}
