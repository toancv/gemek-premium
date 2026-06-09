/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;

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
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.TicketDetailResponse;
import vn.vtit.gemek.module.ticket.entity.Ticket;
import vn.vtit.gemek.module.ticket.entity.TicketPhoto;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;
import vn.vtit.gemek.module.ticket.repository.TicketPhotoRepository;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;
import vn.vtit.gemek.module.ticket.repository.TicketStatusHistoryRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TicketServiceImpl} — SEC-03/08 phone-strip and SEC-01 presign access.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceImplTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketPhotoRepository photoRepository;
    @Mock private TicketStatusHistoryRepository historyRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private ContractorRepository contractorRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private NotificationService notificationService;

    private TicketServiceImpl service;

    private UUID ticketId;
    private Ticket ticket;
    private User submitter;
    private Apartment apartment;

    @BeforeEach
    void setUp() {
        service = new TicketServiceImpl(
                ticketRepository, photoRepository, historyRepository,
                apartmentRepository, userRepository, residentRepository,
                contractorRepository, fileStorageService, notificationService);

        ticketId = UUID.randomUUID();
        Block block = new Block();
        block.setId(UUID.randomUUID());
        block.setName("Block A");
        apartment = new Apartment();
        apartment.setId(UUID.randomUUID());
        apartment.setBlock(block);
        apartment.setUnitNumber("101");

        submitter = new User();
        submitter.setId(UUID.randomUUID());
        submitter.setFullName("Jane Resident");
        submitter.setPhone("0901234567");
        submitter.setRole(UserRole.RESIDENT);
        submitter.setActive(true);

        ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setApartment(apartment);
        ticket.setSubmittedBy(submitter);
        ticket.setStatus(TicketStatus.NEW);
        ticket.setTitle("Test Ticket");

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(photoRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(historyRepository.findByTicketIdOrderByChangedAtAsc(ticketId)).thenReturn(List.of());
        // fileStorageService.presign is called for each photo — return empty string when no photos
        when(fileStorageService.presign(any())).thenReturn("");
    }

    // -------------------------------------------------------------------------
    // SEC-03/08: phone strip for TECHNICIAN and BOARD_MEMBER
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketDetail: TECHNICIAN caller — submitter phone is null (SEC-03)")
    void getTicketDetail_technicianCaller_phoneStripped() {
        UUID techId = UUID.randomUUID();
        // Ticket is NEW → TECHNICIAN passes enforceReadAccess (isNew=true branch)
        TicketDetailResponse response = service.getTicketDetail(ticketId, techId, "TECHNICIAN");

        assertThat(response.getSubmittedBy().getPhone()).isNull();
    }

    @Test
    @DisplayName("getTicketDetail: BOARD_MEMBER caller — submitter phone is null (SEC-08)")
    void getTicketDetail_boardMemberCaller_phoneStripped() {
        UUID boardId = UUID.randomUUID();
        TicketDetailResponse response = service.getTicketDetail(ticketId, boardId, "BOARD_MEMBER");

        assertThat(response.getSubmittedBy().getPhone()).isNull();
    }

    @Test
    @DisplayName("getTicketDetail: ADMIN caller — submitter phone included")
    void getTicketDetail_adminCaller_phoneIncluded() {
        UUID adminId = UUID.randomUUID();
        TicketDetailResponse response = service.getTicketDetail(ticketId, adminId, "ADMIN");

        assertThat(response.getSubmittedBy().getPhone()).isEqualTo("0901234567");
    }

    @Test
    @DisplayName("getTicketDetail: RESIDENT caller same apartment — phone included")
    void getTicketDetail_residentSameApartment_phoneIncluded() {
        UUID residentUserId = UUID.randomUUID();
        Resident resident = new Resident();
        resident.setApartment(apartment);

        when(residentRepository.findActiveByUserId(residentUserId))
                .thenReturn(Optional.of(resident));

        TicketDetailResponse response = service.getTicketDetail(ticketId, residentUserId, "RESIDENT");

        assertThat(response.getSubmittedBy().getPhone()).isEqualTo("0901234567");
    }

    @Test
    @DisplayName("getTicketDetail: RESIDENT caller different apartment — throws FORBIDDEN (SEC-01 variant)")
    void getTicketDetail_residentDifferentApartment_throwsForbidden() {
        UUID otherResidentUserId = UUID.randomUUID();
        Apartment otherApartment = new Apartment();
        otherApartment.setId(UUID.randomUUID());
        Resident otherResident = new Resident();
        otherResident.setApartment(otherApartment);

        when(residentRepository.findActiveByUserId(otherResidentUserId))
                .thenReturn(Optional.of(otherResident));

        assertThatThrownBy(() -> service.getTicketDetail(ticketId, otherResidentUserId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // -------------------------------------------------------------------------
    // SEC-01: assertPresignAccess — non-owner RESIDENT
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("assertPresignAccess: photo key not found — throws NOT_FOUND")
    void assertPresignAccess_photoNotFound_throwsNotFound() {
        when(photoRepository.findByFileUrl("tickets/missing.jpg")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertPresignAccess("tickets/missing.jpg", UUID.randomUUID(), "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("assertPresignAccess: non-owner RESIDENT — throws FORBIDDEN (SEC-01 regression guard)")
    void assertPresignAccess_nonOwnerResident_throwsForbidden() {
        UUID nonOwnerId = UUID.randomUUID();
        Apartment otherApartment = new Apartment();
        otherApartment.setId(UUID.randomUUID());
        Resident nonOwner = new Resident();
        nonOwner.setApartment(otherApartment);

        TicketPhoto photo = new TicketPhoto();
        photo.setTicket(ticket);
        photo.setFileUrl("tickets/secret.jpg");

        when(photoRepository.findByFileUrl("tickets/secret.jpg")).thenReturn(Optional.of(photo));
        when(residentRepository.findActiveByUserId(nonOwnerId)).thenReturn(Optional.of(nonOwner));

        assertThatThrownBy(() -> service.assertPresignAccess("tickets/secret.jpg", nonOwnerId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("assertPresignAccess: ADMIN caller — no exception thrown")
    void assertPresignAccess_adminCaller_noException() {
        TicketPhoto photo = new TicketPhoto();
        photo.setTicket(ticket);
        photo.setFileUrl("tickets/photo.jpg");

        when(photoRepository.findByFileUrl("tickets/photo.jpg")).thenReturn(Optional.of(photo));

        // ADMIN is unrestricted — must complete without exception
        service.assertPresignAccess("tickets/photo.jpg", UUID.randomUUID(), "ADMIN");
    }

    // -------------------------------------------------------------------------
    // rateTicket — status not DONE → INVALID_STATUS_TRANSITION
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rateTicket — ticket not DONE throws INVALID_STATUS_TRANSITION")
    void rateTicket_notDone_throwsInvalidStatusTransition() {
        ticket.setStatus(TicketStatus.NEW);
        RateTicketRequest req = new RateTicketRequest(5, null);

        assertThatThrownBy(() -> service.rateTicket(ticketId, req, submitter.getId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    // -------------------------------------------------------------------------
    // rateTicket — already rated → TICKET_ALREADY_RATED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rateTicket — ticket already rated throws TICKET_ALREADY_RATED")
    void rateTicket_alreadyRated_throwsTicketAlreadyRated() {
        ticket.setStatus(TicketStatus.DONE);
        ticket.setRating((short) 4);
        RateTicketRequest req = new RateTicketRequest(5, null);

        assertThatThrownBy(() -> service.rateTicket(ticketId, req, submitter.getId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TICKET_ALREADY_RATED));
    }
}
