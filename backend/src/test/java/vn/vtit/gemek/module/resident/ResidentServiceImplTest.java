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
import org.springframework.security.crypto.password.PasswordEncoder;
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
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.resident.repository.ResidentHistoryRepository;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResidentServiceImpl}.
 *
 * <p>Covers: duplicate-email CONFLICT, apartment NOT_FOUND, RESIDENT role FORBIDDEN,
 * double move-out CONFLICT, and getMyResident guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResidentServiceImplTest {

    @Mock private ResidentRepository residentRepository;
    @Mock private ResidentHistoryRepository historyRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ResidentMapper residentMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationRepository notificationRepository;

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
                apartmentRepository, userRepository,
                residentMapper, passwordEncoder, notificationRepository);

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
    // createResident — duplicate email → CONFLICT
    // =========================================================================

    @Test
    @DisplayName("createResident — duplicate email throws CONFLICT")
    void createResident_duplicateEmail_throwsConflict() {
        when(userRepository.existsByPhone("0900000001")).thenReturn(false);
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        CreateResidentRequest request = new CreateResidentRequest(
                "Test User", "dup@test.com", "Pass@123456",
                "0900000001", null,
                apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        assertThatThrownBy(() -> service.createResident(request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    // =========================================================================
    // createResident — apartment not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("createResident — apartment not found throws NOT_FOUND")
    void createResident_apartmentNotFound_throwsNotFound() {
        when(userRepository.existsByPhone("0900000001")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(apartmentRepository.findById(apartmentId)).thenReturn(Optional.empty());

        CreateResidentRequest request = new CreateResidentRequest(
                "Test User", "new@test.com", "Pass@123456",
                "0900000001", null,
                apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        assertThatThrownBy(() -> service.createResident(request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // createResident — success path saves user then resident
    // =========================================================================

    @Test
    @DisplayName("createResident — valid request saves user and resident, returns mapped response")
    void createResident_validRequest_savesUserAndResident() {
        when(userRepository.existsByPhone("0900000001")).thenReturn(false);
        when(userRepository.existsByEmail("ok@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass@123456")).thenReturn("$2a$hashed");
        when(apartmentRepository.findById(apartmentId)).thenReturn(Optional.of(apartment));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(residentRepository.save(any(Resident.class))).thenReturn(resident);
        ResidentResponse expected = ResidentResponse.builder().id(residentId).build();
        when(residentMapper.toResponse(resident)).thenReturn(expected);

        CreateResidentRequest request = new CreateResidentRequest(
                "Nguyen Van A", "ok@test.com", "Pass@123456",
                "0900000001", null,
                apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        ResidentResponse result = service.createResident(request, UUID.randomUUID());

        assertThat(result.getId()).isEqualTo(residentId);
    }

    // =========================================================================
    // createResident — non-canonical phone stored as canonical
    // =========================================================================

    @Test
    @DisplayName("createResident — non-canonical phone (+84 prefix) is stored as canonical 0xxxxxxxxx")
    void createResident_nonCanonicalPhone_storesCanonical() {
        when(userRepository.existsByPhone("0900000001")).thenReturn(false);
        when(userRepository.existsByEmail("ok@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass@123456")).thenReturn("$2a$hashed");
        when(apartmentRepository.findById(apartmentId)).thenReturn(Optional.of(apartment));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(residentRepository.save(any(Resident.class))).thenReturn(resident);
        when(residentMapper.toResponse(resident)).thenReturn(ResidentResponse.builder().id(residentId).build());

        CreateResidentRequest request = new CreateResidentRequest(
                "Nguyen Van A", "ok@test.com", "Pass@123456",
                "+84900000001", null,
                apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        service.createResident(request, UUID.randomUUID());

        ArgumentCaptor<User> captor = forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("0900000001");
    }

    // =========================================================================
    // createResident — null email accepted (email optional since V12 / 4237cba)
    // =========================================================================

    @Test
    @DisplayName("createResident — null email is accepted; user persisted with null email")
    void createResident_nullEmail_savedWithNullEmail() {
        when(userRepository.existsByPhone("0900000001")).thenReturn(false);
        when(passwordEncoder.encode("Pass@123456")).thenReturn("$2a$hashed");
        when(apartmentRepository.findById(apartmentId)).thenReturn(Optional.of(apartment));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(residentRepository.save(any(Resident.class))).thenReturn(resident);
        when(residentMapper.toResponse(resident)).thenReturn(ResidentResponse.builder().id(residentId).build());

        CreateResidentRequest request = new CreateResidentRequest(
                "Nguyen Van A", null, "Pass@123456",
                "0900000001", LocalDate.of(1990, 1, 1),
                apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        service.createResident(request, UUID.randomUUID());

        ArgumentCaptor<User> captor = forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    // =========================================================================
    // createResident — duplicate phone → PHONE_ALREADY_EXISTS
    // =========================================================================

    @Test
    @DisplayName("createResident — duplicate phone throws PHONE_ALREADY_EXISTS")
    void createResident_duplicatePhone_throwsPhoneAlreadyExists() {
        when(userRepository.existsByPhone("0900000001")).thenReturn(true);

        CreateResidentRequest request = new CreateResidentRequest(
                "Test User", "new@test.com", "Pass@123456",
                "0900000001", null,
                apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), false, null);

        assertThatThrownBy(() -> service.createResident(request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS));
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
    @DisplayName("moveOut — resident already has moveOutDate set throws RESIDENT_ALREADY_MOVED_OUT")
    void moveOut_alreadyMovedOut_throwsResidentAlreadyMovedOut() {
        resident.setMoveOutDate(LocalDate.of(2026, 3, 1));
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));

        MoveOutRequest request = new MoveOutRequest(LocalDate.of(2026, 4, 1), null);

        assertThatThrownBy(() -> service.moveOut(residentId, request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESIDENT_ALREADY_MOVED_OUT));
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

    // =========================================================================
    // moveOut — conditional user deactivation (backlog (d) follow-up)
    // =========================================================================

    @Test
    @DisplayName("moveOut — user with no other active residency is deactivated in the same transaction")
    void moveOut_noOtherActiveResidency_deactivatesUser() {
        user.setActive(true);
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));
        // After this move-out, the user has no remaining active residency.
        when(residentRepository.existsActiveByUserId(userId)).thenReturn(false);
        when(residentMapper.toResponse(any(Resident.class)))
                .thenReturn(ResidentResponse.builder().id(residentId).build());

        MoveOutRequest request = new MoveOutRequest(LocalDate.of(2026, 4, 1), null);
        service.moveOut(residentId, request, UUID.randomUUID());

        assertThat(user.isActive()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("moveOut — user with another active residency stays active (safe guard)")
    void moveOut_anotherActiveResidency_keepsUserActive() {
        user.setActive(true);
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));
        // The user still lives in another apartment — must NOT be locked out.
        when(residentRepository.existsActiveByUserId(userId)).thenReturn(true);
        when(residentMapper.toResponse(any(Resident.class)))
                .thenReturn(ResidentResponse.builder().id(residentId).build());

        MoveOutRequest request = new MoveOutRequest(LocalDate.of(2026, 4, 1), null);
        service.moveOut(residentId, request, UUID.randomUUID());

        assertThat(user.isActive()).isTrue();
        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("moveOut — resident with no linked user account does not deactivate and does not error")
    void moveOut_noLinkedUser_noDeactivation() {
        resident.setUser(null);
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(residentMapper.toResponse(any(Resident.class)))
                .thenReturn(ResidentResponse.builder().id(residentId).build());

        MoveOutRequest request = new MoveOutRequest(LocalDate.of(2026, 4, 1), null);
        service.moveOut(residentId, request, UUID.randomUUID());

        // No user to touch; the residency-check query is never run.
        verify(userRepository, never()).save(any(User.class));
        verify(residentRepository, never()).existsActiveByUserId(any());
    }

    @Test
    @DisplayName("moveOut — deactivation failure propagates so the whole move-out rolls back")
    void moveOut_deactivationThrows_propagates() {
        user.setActive(true);
        when(residentRepository.findById(residentId)).thenReturn(Optional.of(resident));
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(residentRepository.existsActiveByUserId(userId)).thenReturn(false);
        // Simulate the deactivation write failing inside the transaction.
        when(userRepository.save(user)).thenThrow(new RuntimeException("DB write failed"));

        MoveOutRequest request = new MoveOutRequest(LocalDate.of(2026, 4, 1), null);

        assertThatThrownBy(() -> service.moveOut(residentId, request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);

        // Method aborted before mapping the response — proves the failure is not swallowed,
        // so the @Transactional boundary rolls the move-out back (move_out_date not committed).
        verify(residentMapper, never()).toResponse(any());
    }
}
