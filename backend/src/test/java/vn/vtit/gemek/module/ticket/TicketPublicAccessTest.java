/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationSubscription;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.entity.SubscriptionJoinedVia;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.dto.AssignTicketRequest;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.TicketDetailResponse;
import vn.vtit.gemek.module.ticket.dto.TicketSummaryResponse;
import vn.vtit.gemek.module.ticket.dto.UpdateTicketStatusRequest;
import vn.vtit.gemek.module.ticket.entity.PhotoPhase;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketPhoto;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;
import vn.vtit.gemek.module.ticket.repository.TicketPhotoRepository;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * N3 P5 — public/private ticket tests: presign denial on public tickets (the heart
 * of P5), field-level redaction (G8), resident list scoping with the visibility
 * filter, and follow/unfollow joining the P3 dispatch thread.
 *
 * <p>Class-level {@code @Transactional} rolls all fixtures back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketPublicAccessTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketPhotoRepository photoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Block block;
    private Apartment apartmentA;
    private Apartment apartmentB;
    private User creator;
    private User householdMember;
    private User outsider;
    private User admin;
    private User staff;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        when(fileStorageService.presign(anyString())).thenReturn("http://minio/presigned-url");

        block = new Block();
        block.setName("P5-" + tag);
        block = blockRepository.save(block);

        apartmentA = saveApartment("P5A-" + tag);
        apartmentB = saveApartment("P5B-" + tag);

        // Phone prefix "02" — unused by other test classes (03/04/05/06/07/08 taken).
        creator = saveUser(tag + 1, UserRole.RESIDENT);
        householdMember = saveUser(tag + 2, UserRole.RESIDENT);
        outsider = saveUser(tag + 3, UserRole.RESIDENT);
        admin = saveUser(tag + 4, UserRole.ADMIN);
        staff = saveUser(tag + 5, UserRole.TECHNICIAN);

        saveResidency(creator, apartmentA);
        saveResidency(householdMember, apartmentA);
        saveResidency(outsider, apartmentB);
    }

    // =========================================================================
    // SECURITY — presign stays household/staff-only on PUBLIC tickets (F-05 gate)
    // =========================================================================

    @Test
    @DisplayName("SECURITY — outsider resident: presign on a PUBLIC ticket's photo DENIED, redacted detail readable")
    void publicTicket_outsiderPresignDenied_butRedactedDetailReadable() {
        TicketDetailResponse ticket = createTicket(true);
        String objectKey = savePhoto(ticket.getId());

        // The pair of asserts at the heart of P5: photo NO, redacted detail YES.
        assertThatThrownBy(() -> ticketService
                .assertPresignAccess(objectKey, outsider.getId(), "RESIDENT"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        TicketDetailResponse detail = ticketService
                .getTicketDetail(ticket.getId(), outsider.getId(), "RESIDENT");
        assertThat(detail.getId()).isEqualTo(ticket.getId());
        assertThat(detail.getPhotos()).isEmpty();
    }

    @Test
    @DisplayName("Private ticket — outsider resident gets FORBIDDEN on detail (pre-P5 rule intact)")
    void privateTicket_outsiderDetail_forbidden() {
        TicketDetailResponse ticket = createTicket(false);

        assertThatThrownBy(() -> ticketService
                .getTicketDetail(ticket.getId(), outsider.getId(), "RESIDENT"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // =========================================================================
    // Redaction — field level (G8)
    // =========================================================================

    @Test
    @DisplayName("Redacted detail — «Cư dân» placeholder; unit number, phone, photos, changedBy, rating comment absent")
    void publicTicket_outsiderDetail_isRedactedFieldLevel() {
        TicketDetailResponse ticket = createRatedPublicTicket();

        TicketDetailResponse detail = ticketService
                .getTicketDetail(ticket.getId(), outsider.getId(), "RESIDENT");

        // Visible per G8.
        assertThat(detail.getTitle()).isEqualTo(ticket.getTitle());
        assertThat(detail.getDescription()).isEqualTo("P5 fixture");
        assertThat(detail.getCategory()).isEqualTo(TicketCategory.MAINTENANCE_REPAIR);
        assertThat(detail.getStatus()).isEqualTo(TicketStatus.DONE);
        assertThat(detail.getApartment().getBlock().getName()).isEqualTo(block.getName());
        assertThat(detail.getResolutionNotes()).isEqualTo("Fixed the leak");
        assertThat(detail.getCreatedAt()).isNotNull();
        assertThat(detail.getStatusHistory()).isNotEmpty();
        assertThat(detail.getStatusHistory()).allSatisfy(entry -> {
            assertThat(entry.getChangedAt()).isNotNull();
            assertThat(entry.getNewStatus()).isNotNull();
            // Hidden inside history: staff identities and notes.
            assertThat(entry.getChangedBy()).isNull();
            assertThat(entry.getNotes()).isNull();
        });

        // Hidden per G8.
        assertThat(detail.getSubmittedBy().getFullName()).isEqualTo("Cư dân");
        assertThat(detail.getSubmittedBy().getId()).isNull();
        assertThat(detail.getSubmittedBy().getPhone()).isNull();
        assertThat(detail.getApartment().getUnitNumber()).isNull();
        assertThat(detail.getApartment().getId()).isNull();
        assertThat(detail.getPhotos()).isEmpty();
        assertThat(detail.getAssignedToUser()).isNull();
        assertThat(detail.getRatingComment()).isNull();
    }

    @Test
    @DisplayName("Full view intact — household member and admin see name, unit, photos, rating comment")
    void publicTicket_householdAndAdmin_getFullView() {
        TicketDetailResponse ticket = createRatedPublicTicket();
        savePhoto(ticket.getId());

        for (var caller : List.of(
                new Object[]{householdMember.getId(), "RESIDENT"},
                new Object[]{admin.getId(), "ADMIN"})) {
            TicketDetailResponse detail = ticketService
                    .getTicketDetail(ticket.getId(), (UUID) caller[0], (String) caller[1]);
            assertThat(detail.getSubmittedBy().getFullName()).isEqualTo(creator.getFullName());
            assertThat(detail.getApartment().getUnitNumber())
                    .isEqualTo(apartmentA.getUnitNumber());
            assertThat(detail.getPhotos()).hasSize(1);
            assertThat(detail.getRatingComment()).isEqualTo("Rất tốt");
            assertThat(detail.getAssignedToUser().getId()).isEqualTo(staff.getId());
        }
    }

    // =========================================================================
    // List scoping + visibility filter
    // =========================================================================

    @Test
    @DisplayName("Scoping — default list = own household only; community = public only, redacted; private never leaks")
    void residentList_scopingAndVisibilityFilter() {
        TicketDetailResponse publicTicket = createTicket(true);
        TicketDetailResponse privateTicket = createTicket(false);
        TicketDetailResponse ownTicket = ticketService.createTicket(
                createRequest(apartmentB.getId(), false), outsider.getId(), "RESIDENT");

        // Default (null visibility) — pre-P5 behavior: own household only.
        List<UUID> defaultIds = listIds(outsider.getId(), null);
        assertThat(defaultIds).contains(ownTicket.getId());
        assertThat(defaultIds).doesNotContain(publicTicket.getId(), privateTicket.getId());

        // Explicit mine — identical to default.
        assertThat(listIds(outsider.getId(), "mine")).containsExactlyElementsOf(defaultIds);

        // Community — the public ticket appears (redacted), the private one never does.
        List<TicketSummaryResponse> community = list(outsider.getId(), "community");
        assertThat(community).extracting(TicketSummaryResponse::getId)
                .contains(publicTicket.getId())
                .doesNotContain(privateTicket.getId(), ownTicket.getId());
        TicketSummaryResponse row = community.stream()
                .filter(summary -> summary.getId().equals(publicTicket.getId()))
                .findFirst().orElseThrow();
        assertThat(row.getSubmittedBy().getFullName()).isEqualTo("Cư dân");
        assertThat(row.getSubmittedBy().getId()).isNull();
        assertThat(row.getApartment().getUnitNumber()).isNull();
        assertThat(row.getApartment().getBlock().getName()).isEqualTo(block.getName());

        // Invalid visibility value — rejected.
        assertThatThrownBy(() -> list(outsider.getId(), "everything"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("Scoping — admin list unchanged: full rows with submitter name and unit number")
    void adminList_remainsUnredacted() {
        TicketDetailResponse publicTicket = createTicket(true);

        List<TicketSummaryResponse> rows = ticketService.listTickets(
                        admin.getId(), "ADMIN", null, null, null, null, apartmentA.getId(), pageable())
                .getData();
        assertThat(rows).extracting(TicketSummaryResponse::getId).contains(publicTicket.getId());
        TicketSummaryResponse row = rows.stream()
                .filter(summary -> summary.getId().equals(publicTicket.getId()))
                .findFirst().orElseThrow();
        assertThat(row.getSubmittedBy().getFullName()).isEqualTo(creator.getFullName());
        assertThat(row.getApartment().getUnitNumber()).isEqualTo(apartmentA.getUnitNumber());
    }

    // =========================================================================
    // Follow / unfollow
    // =========================================================================

    @Test
    @DisplayName("Follow public ticket — FOLLOWER row written; follow and unfollow both idempotent")
    void followPublicTicket_writesFollowerRow_idempotentBothWays() {
        TicketDetailResponse ticket = createTicket(true);

        ticketService.followTicket(ticket.getId(), outsider.getId());
        assertThatCode(() -> ticketService.followTicket(ticket.getId(), outsider.getId()))
                .doesNotThrowAnyException();

        List<NotificationSubscription> rows = subscriptionRows(ticket.getId(), outsider.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getJoinedVia()).isEqualTo(SubscriptionJoinedVia.FOLLOWER);

        ticketService.unfollowTicket(ticket.getId(), outsider.getId());
        assertThat(subscriptionRows(ticket.getId(), outsider.getId())).isEmpty();
        assertThatCode(() -> ticketService.unfollowTicket(ticket.getId(), outsider.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Follow a private invisible ticket — NOT_FOUND, no existence leak")
    void followPrivateInvisibleTicket_notFound() {
        TicketDetailResponse ticket = createTicket(false);

        assertThatThrownBy(() -> ticketService.followTicket(ticket.getId(), outsider.getId()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(subscriptionRows(ticket.getId(), outsider.getId())).isEmpty();
    }

    @Test
    @DisplayName("INTEGRATION — follower receives the C4 status-change notification (P5 ties into P3)")
    void follower_receivesStatusChangeNotification() {
        TicketDetailResponse ticket = createTicket(true);
        ticketService.followTicket(ticket.getId(), outsider.getId());

        ticketService.updateStatus(ticket.getId(),
                UpdateTicketStatusRequest.builder().status(TicketStatus.CANCELLED).build(),
                admin.getId(), "ADMIN");

        List<Notification> rows = entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id"
                                + " AND n.user.id = :userId AND n.type = :type",
                        Notification.class)
                .setParameter("id", ticket.getId())
                .setParameter("userId", outsider.getId())
                .setParameter("type", NotificationType.TICKET_STATUS_CHANGED)
                .getResultList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBody()).isEqualTo(
                "Phản ánh \"" + ticket.getTitle() + "\" chuyển sang trạng thái: Đã hủy.");
    }

    // =========================================================================
    // G3 — immutability
    // =========================================================================

    @Test
    @DisplayName("G3 — no mutation path: flag persists through assign, status updates and rating")
    void isPublicFlag_surviveAllMutationEndpoints() {
        TicketDetailResponse ticket = createRatedPublicTicket();

        // Every mutating endpoint has run; the flag is untouched.
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().isPublic()).isTrue();
    }

    // =========================================================================
    // Fixture and query helpers
    // =========================================================================

    /**
     * Creates a ticket in apartment A as the fixture creator.
     *
     * @param isPublic the community-visibility flag.
     * @return the created ticket detail.
     */
    private TicketDetailResponse createTicket(boolean isPublic) {
        return ticketService.createTicket(
                createRequest(apartmentA.getId(), isPublic), creator.getId(), "RESIDENT");
    }

    /**
     * Creates a public ticket and drives it through assign → IN_PROGRESS → DONE
     * (with resolution notes) → rated with a comment.
     *
     * @return the created ticket detail (state at creation time).
     */
    private TicketDetailResponse createRatedPublicTicket() {
        TicketDetailResponse ticket = createTicket(true);
        ticketService.assignTicket(ticket.getId(),
                AssignTicketRequest.builder().assignedToUserId(staff.getId()).build(),
                admin.getId());
        ticketService.updateStatus(ticket.getId(),
                UpdateTicketStatusRequest.builder().status(TicketStatus.IN_PROGRESS).build(),
                staff.getId(), "TECHNICIAN");
        ticketService.updateStatus(ticket.getId(),
                UpdateTicketStatusRequest.builder()
                        .status(TicketStatus.DONE)
                        .resolutionNotes("Fixed the leak")
                        .notes("done note")
                        .build(),
                staff.getId(), "TECHNICIAN");
        ticketService.rateTicket(ticket.getId(),
                RateTicketRequest.builder().rating(5).comment("Rất tốt").build(),
                creator.getId());
        return ticket;
    }

    /**
     * Builds a create request.
     *
     * @param apartmentId target apartment.
     * @param isPublic    community-visibility flag.
     * @return the request.
     */
    private CreateTicketRequest createRequest(UUID apartmentId, boolean isPublic) {
        return CreateTicketRequest.builder()
                .apartmentId(apartmentId)
                .category(TicketCategory.MAINTENANCE_REPAIR)
                .title("N3P5 " + System.nanoTime())
                .description("P5 fixture")
                .isPublic(isPublic)
                .build();
    }

    /**
     * Persists a photo row for the ticket without touching MinIO.
     *
     * @param ticketId the ticket UUID.
     * @return the photo's MinIO object key.
     */
    private String savePhoto(UUID ticketId) {
        TicketPhoto photo = new TicketPhoto();
        photo.setTicket(ticketRepository.findById(ticketId).orElseThrow());
        photo.setFileUrl("tickets/" + ticketId + "/before/" + UUID.randomUUID() + ".jpg");
        photo.setFileName("p5.jpg");
        photo.setMimeType("image/jpeg");
        photo.setFileSize(1024);
        photo.setPhase(PhotoPhase.BEFORE);
        photo.setUploadedBy(creator);
        return photoRepository.save(photo).getFileUrl();
    }

    /**
     * Lists ticket summaries for a resident caller with the given visibility filter.
     *
     * @param userId     the resident caller.
     * @param visibility the visibility filter value, or {@code null}.
     * @return the page content.
     */
    private List<TicketSummaryResponse> list(UUID userId, String visibility) {
        return ticketService.listTickets(userId, "RESIDENT", visibility,
                null, null, null, null, pageable()).getData();
    }

    /**
     * Lists ticket IDs for a resident caller with the given visibility filter.
     *
     * @param userId     the resident caller.
     * @param visibility the visibility filter value, or {@code null}.
     * @return the visible ticket IDs.
     */
    private List<UUID> listIds(UUID userId, String visibility) {
        return list(userId, visibility).stream().map(TicketSummaryResponse::getId).toList();
    }

    /**
     * Default page request matching the controller's sort.
     *
     * @return the pageable.
     */
    private Pageable pageable() {
        return PageRequest.of(0, 100,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    /**
     * Lists subscription rows for one user on one ticket.
     *
     * @param ticketId the ticket UUID.
     * @param userId   the user UUID.
     * @return matching subscription rows.
     */
    private List<NotificationSubscription> subscriptionRows(UUID ticketId, UUID userId) {
        return entityManager.createQuery(
                        "SELECT s FROM NotificationSubscription s WHERE s.entityType = 'Ticket'"
                                + " AND s.entityId = :ticketId AND s.user.id = :userId",
                        NotificationSubscription.class)
                .setParameter("ticketId", ticketId)
                .setParameter("userId", userId)
                .getResultList();
    }

    /**
     * Persists an apartment in the fixture block.
     *
     * @param unitNumber the unique unit number.
     * @return the saved apartment.
     */
    private Apartment saveApartment(String unitNumber) {
        Apartment apartment = new Apartment();
        apartment.setBlock(block);
        apartment.setFloor((short) 1);
        apartment.setUnitNumber(unitNumber);
        return apartmentRepository.save(apartment);
    }

    /**
     * Persists an active user with the given role and a unique "02"-prefixed phone.
     *
     * @param tag  uniqueness tag.
     * @param role the role to assign.
     * @return the saved user.
     */
    private User saveUser(long tag, UserRole role) {
        User user = new User();
        user.setPhone("02" + String.format("%08d", Math.abs(tag) % 100_000_000L));
        user.setFullName("P5 " + role + " " + tag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    /**
     * Persists an active residency.
     *
     * @param user   the resident user.
     * @param target the apartment.
     */
    private void saveResidency(User user, Apartment target) {
        Resident residency = new Resident();
        residency.setUser(user);
        residency.setApartment(target);
        residency.setType(ResidentType.OWNER);
        residency.setMoveInDate(LocalDate.now().minusYears(1));
        residentRepository.save(residency);
    }
}
