/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident;

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
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.dto.MoveOutRequest;
import vn.vtit.gemek.module.resident.dto.ResidentResponse;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.mapper.ResidentMapper;
import vn.vtit.gemek.module.resident.repository.ResidentHistoryRepository;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResidentServiceImpl} — GAP-08 assignment guards.
 *
 * <p>Verifies: duplicate-active-resident CONFLICT, user/apartment NOT_FOUND guards,
 * RESIDENT role cross-record FORBIDDEN, and double move-out CONFLICT.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResidentServiceImplTest {

    @Mock private ResidentRepository residentRepository;
    @Mock private ResidentHistoryRepository historyRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ResidentMapper residentMapper;

    private ResidentServiceImpl service;

    private UUID userId;
    private UUID apartmentId;
    private UUID residentId;
    private User user;
    private Apartment apartment;
    private Resident resident;

    @BeforeEach
    void setUp() {
        service = new ResidentServiceImpl(
                residentRepository, historyRepository,
                apartmentRepository, userRepository, residentMapper);

        userId = UUID.randomUUID();
        apartmentId = UUID.randomUUID();
        residentId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setFullName("Nguyen Van A");
        user.setEmail("van.a@gemek.vn");

        apartment = new Apartment();
        apartment.setId(apartmentId);

        resident = new Resident();
        resident.setId(residentId);
        resident.setUser(user);
        resident.setApartment(apartment);
        resident.setType(ResidentType.OWNER);
        resident.setMoveInDate(LocalDate.of(2026, 1, 1));
        resident.setCreatedAt(OffsetDateTime.now());
        resident.setUpdatedAt(OffsetDateTime.now());
    }

    // =========================================================================
    // createResident — user already active in another apartment → CONFLICT
    // =========================================================================

    @Test
    @DisplayName("createResident — user already active resident throws CONFLICT")
    void createResident_userAlreadyActiveResident_throwsConflict() {
        when(residentRepository.existsActiveByUserId(userId)).thenReturn(true);

        CreateResidentRequest request = new CreateResidentRequest(
                userId, apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        assertThatThrownBy(() -> service.createResident(request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    // =========================================================================
    // createResident — user not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("createResident — user not found throws NOT_FOUND")
    void createResident_userNotFound_throwsNotFound() {
        when(residentRepository.existsActiveByUserId(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CreateResidentRequest request = new CreateResidentRequest(
                userId, apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        assertThatThrownBy(() -> service.createResident(request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // createResident — apartment not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("createResident — apartment not found throws NOT_FOUND")
    void createResident_apartmentNotFound_throwsNotFound() {
        when(residentRepository.existsActiveByUserId(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(apartmentRepository.findById(apartmentId)).thenReturn(Optional.empty());

        CreateResidentRequest request = new CreateResidentRequest(
                userId, apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        assertThatThrownBy(() -> service.createResident(request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // getResident — RESIDENT role accessing another user's record → FORBIDDEN
    // =========================================================================

    @Test
    @DisplayName("getResident — RESIDENT viewing another resident's record throws FORBIDDEN")
    void getResident_residentRoleViewingOthersRecord_throwsForbidden() {
        UUID differentUserId = UUID.randomUUID();
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.getResident(residentId, differentUserId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // =========================================================================
    // getResident — resident not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("getResident — unknown resident ID throws NOT_FOUND")
    void getResident_residentNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(residentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResident(unknownId, userId, "ADMIN"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // moveOut — resident already moved out → CONFLICT
    // =========================================================================

    @Test
    @DisplayName("moveOut — resident already has moveOutDate set throws CONFLICT")
    void moveOut_alreadyMovedOut_throwsConflict() {
        resident.setMoveOutDate(LocalDate.of(2026, 3, 1));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));

        MoveOutRequest request = new MoveOutRequest(LocalDate.of(2026, 4, 1), null);

        assertThatThrownBy(() -> service.moveOut(residentId, request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    // =========================================================================
    // moveOut — resident not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("moveOut — unknown resident ID throws NOT_FOUND")
    void moveOut_residentNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(residentRepository.findById(unknownId)).thenReturn(Optional.empty());

        MoveOutRequest request = new MoveOutRequest(LocalDate.of(2026, 4, 1), null);

        assertThatThrownBy(() -> service.moveOut(unknownId, request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // getMyResident — active residency found → returns response
    // =========================================================================

    @Test
    @DisplayName("getMyResident — active residency found returns mapped response")
    void getMyResident_activeResident_returnsResponse() {
        when(residentRepository.findActiveByUserId(userId)).thenReturn(Optional.of(resident));
        ResidentResponse expected = ResidentResponse.builder().id(residentId).build();
        when(residentMapper.toResponse(resident)).thenReturn(expected);

        ResidentResponse result = service.getMyResident(userId);

        assertThat(result.getId()).isEqualTo(residentId);
    }

    // =========================================================================
    // getMyResident — no active residency → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("getMyResident — no active residency throws NOT_FOUND")
    void getMyResident_noActiveResidency_throwsNotFound() {
        when(residentRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyResident(userId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }
}
