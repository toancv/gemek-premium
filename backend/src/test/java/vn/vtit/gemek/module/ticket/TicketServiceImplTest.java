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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.announcement.AnnouncementService;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.notification.SubscriptionService;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.TicketDetailResponse;
import vn.vtit.gemek.module.ticket.dto.TicketSummaryResponse;
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
    @Mock private NotificationRepository notificationRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private AnnouncementService announcementService;

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
                contractorRepository, fileStorageService, notificationService,
                notificationRepository, subscriptionService, announcementService);

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
        // Per-context guard: active residency in the ticket's apartment.
        when(residentRepository.existsActiveByUserIdAndApartmentId(residentUserId, apartment.getId()))
                .thenReturn(true);

        TicketDetailResponse response = service.getTicketDetail(ticketId, residentUserId, "RESIDENT");

        assertThat(response.getSubmittedBy().getPhone()).isEqualTo("0901234567");
    }

    @Test
    @DisplayName("getTicketDetail: RESIDENT caller different apartment — throws FORBIDDEN (SEC-01 variant)")
    void getTicketDetail_residentDifferentApartment_throwsForbidden() {
        UUID otherResidentUserId = UUID.randomUUID();
        // Per-context guard: NOT an active resident of the ticket's apartment.
        when(residentRepository.existsActiveByUserIdAndApartmentId(otherResidentUserId, apartment.getId()))
                .thenReturn(false);

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
        TicketPhoto photo = new TicketPhoto();
        photo.setTicket(ticket);
        photo.setFileUrl("tickets/secret.jpg");

        when(photoRepository.findByFileUrl("tickets/secret.jpg")).thenReturn(Optional.of(photo));
        // Per-context guard: NOT an active resident of the ticket's apartment.
        when(residentRepository.existsActiveByUserIdAndApartmentId(nonOwnerId, apartment.getId()))
                .thenReturn(false);

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
    // Multi-residency (P1): "mine" scope spans ALL active apartments; per-context guards
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketDetail: RESIDENT active in the ticket's apartment among several — allowed (per-context)")
    void getTicketDetail_residentActiveInThatApartment_allowed() {
        UUID userWithTwoApts = UUID.randomUUID();
        // Per-context membership keys on the ticket's specific apartment, regardless of other residencies.
        when(residentRepository.existsActiveByUserIdAndApartmentId(userWithTwoApts, apartment.getId()))
                .thenReturn(true);

        TicketDetailResponse response = service.getTicketDetail(ticketId, userWithTwoApts, "RESIDENT");

        assertThat(response.getSubmittedBy().getPhone()).isEqualTo("0901234567");
    }

    @Test
    @DisplayName("listTickets (mine): RESIDENT with TWO active apartments — tickets of BOTH are un-redacted")
    void listTickets_residentTwoApartments_bothApartmentsUnredacted() {
        UUID userTwoApts = UUID.randomUUID();

        Block blockB = new Block();
        blockB.setId(UUID.randomUUID());
        blockB.setName("Block B");
        Apartment apartmentB = new Apartment();
        apartmentB.setId(UUID.randomUUID());
        apartmentB.setBlock(blockB);
        apartmentB.setUnitNumber("202");
        Ticket ticketB = new Ticket();
        ticketB.setId(UUID.randomUUID());
        ticketB.setApartment(apartmentB);
        ticketB.setSubmittedBy(submitter);
        ticketB.setStatus(TicketStatus.NEW);
        ticketB.setTitle("B Ticket");

        // "My" apartments = the full active-apartment set (one batch query) — A and B.
        when(residentRepository.findActiveApartmentIdsByUserId(userTwoApts))
                .thenReturn(List.of(apartment.getId(), apartmentB.getId()));
        when(ticketRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(ticket, ticketB)));

        PageResponse<TicketSummaryResponse> page = service.listTickets(
                userTwoApts, "RESIDENT", null, null, null, null, null, null, null,
                PageRequest.of(0, 20));

        // Both tickets un-redacted → real submitter name on both (redacted would be the «Cư dân» label).
        assertThat(page.getData()).extracting(s -> s.getSubmittedBy().getFullName())
                .containsExactly("Jane Resident", "Jane Resident");
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
