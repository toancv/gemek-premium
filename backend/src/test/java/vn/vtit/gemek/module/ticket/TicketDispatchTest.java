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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationSubscriptionRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.dto.AssignTicketRequest;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.RateTicketRequest;
import vn.vtit.gemek.module.ticket.dto.TicketDetailResponse;
import vn.vtit.gemek.module.ticket.dto.UpdateTicketStatusRequest;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * N3 P3 — ticket lifecycle dispatch tests (events C1–C6 of the approved design).
 *
 * <p>Per event: exact recipient set including actor exclusion, row counts, EXACT
 * Vietnamese title/body strings (locked terminology — DONE = «Hoàn tất»), type and
 * reference fields. Also covers live subscription writes (creator on create,
 * assignee on assign), the G4 reassignment rule (old assignee keeps the thread row
 * and still receives C4), and the idempotent double-subscribe path through assign.
 *
 * <p>Class-level {@code @Transactional} rolls all fixtures back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketDispatchTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private NotificationSubscriptionRepository subscriptionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Block block;
    private Apartment apartment;
    private User resident;
    private User admin;
    private User staff1;
    private User staff2;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        block = new Block();
        block.setName("P3-" + tag);
        block = blockRepository.save(block);

        apartment = new Apartment();
        apartment.setBlock(block);
        apartment.setFloor((short) 1);
        apartment.setUnitNumber("P3-" + tag);
        apartment = apartmentRepository.save(apartment);

        // Phone prefix "04" — unused by other test classes (05/06/07/08 taken).
        resident = saveUser(tag + 1, UserRole.RESIDENT);
        admin = saveUser(tag + 2, UserRole.ADMIN);
        staff1 = saveUser(tag + 3, UserRole.TECHNICIAN);
        staff2 = saveUser(tag + 4, UserRole.TECHNICIAN);

        Resident residency = new Resident();
        residency.setUser(resident);
        residency.setApartment(apartment);
        residency.setType(ResidentType.OWNER);
        residency.setMoveInDate(LocalDate.now().minusYears(1));
        residentRepository.save(residency);
    }

    // =========================================================================
    // C1 — ticket created → active admins, actor excluded
    // =========================================================================

    @Test
    @DisplayName("C1 create (resident actor) — one TICKET_CREATED row per active admin, exact VN strings, creator subscribed")
    void createTicket_residentActor_dispatchesToAllActiveAdminsAndSubscribesCreator() {
        TicketDetailResponse ticket = createTicket();

        List<UUID> adminIds = userRepository.findActiveUserIdsByRole(UserRole.ADMIN);
        List<Notification> rows = rowsFor(ticket.getId(), NotificationType.TICKET_CREATED);
        assertThat(rows).hasSize(adminIds.size());
        assertThat(rows).allSatisfy(row -> assertThat(adminIds).contains(row.getUser().getId()));

        Notification adminRow = rowFor(ticket.getId(), NotificationType.TICKET_CREATED, admin.getId());
        assertThat(adminRow.getTitle()).isEqualTo("Phản ánh mới");
        assertThat(adminRow.getBody()).isEqualTo("Phản ánh mới: \"" + ticket.getTitle()
                + "\" — căn hộ " + apartment.getUnitNumber() + ", tòa " + block.getName() + ".");
        assertThat(adminRow.getReferenceId()).isEqualTo(ticket.getId());
        assertThat(adminRow.getReferenceType()).isEqualTo("Ticket");

        // Creator joined the thread; the resident actor received no row.
        assertThat(subscriptionRepository.existsByUserIdAndEntityTypeAndEntityId(
                resident.getId(), "Ticket", ticket.getId())).isTrue();
        assertThat(allRowsForUser(ticket.getId(), resident.getId())).isEmpty();
    }

    @Test
    @DisplayName("C1 create (admin actor) — acting admin excluded from the recipient set")
    void createTicket_adminActor_isExcludedFromRecipients() {
        TicketDetailResponse ticket = ticketService.createTicket(createRequest(), admin.getId(), "ADMIN");

        List<UUID> adminIds = userRepository.findActiveUserIdsByRole(UserRole.ADMIN);
        List<Notification> rows = rowsFor(ticket.getId(), NotificationType.TICKET_CREATED);
        assertThat(rows).hasSize(adminIds.size() - 1);
        assertThat(allRowsForUser(ticket.getId(), admin.getId())).isEmpty();
    }

    // =========================================================================
    // C2 + C3 — assign
    // =========================================================================

    @Test
    @DisplayName("C2+C3 assign — assignee gets TICKET_ASSIGNED, creator gets the acceptance update, actor gets nothing")
    void assignTicket_dispatchesAssigneeAndThreadRows() {
        TicketDetailResponse ticket = createTicket();
        ticketService.assignTicket(ticket.getId(), assignRequest(staff1), admin.getId());

        // C2 — exact strings to the assignee.
        Notification assigneeRow = rowFor(ticket.getId(), NotificationType.TICKET_ASSIGNED, staff1.getId());
        assertThat(assigneeRow.getTitle()).isEqualTo("Phản ánh được phân công");
        assertThat(assigneeRow.getBody())
                .isEqualTo("Phản ánh \"" + ticket.getTitle() + "\" đã được phân công cho bạn.");
        assertThat(assigneeRow.getReferenceType()).isEqualTo("Ticket");

        // C3 — creator (thread snapshot) gets the acceptance update; actor and assignee do not.
        List<Notification> statusRows = rowsFor(ticket.getId(), NotificationType.TICKET_STATUS_CHANGED);
        assertThat(statusRows).hasSize(1);
        assertThat(statusRows.get(0).getUser().getId()).isEqualTo(resident.getId());
        assertThat(statusRows.get(0).getTitle()).isEqualTo("Cập nhật phản ánh");
        assertThat(statusRows.get(0).getBody())
                .isEqualTo("Phản ánh \"" + ticket.getTitle() + "\" đã được tiếp nhận và phân công xử lý.");

        // Assignee joined the thread.
        assertThat(subscriptionRepository.existsByUserIdAndEntityTypeAndEntityId(
                staff1.getId(), "Ticket", ticket.getId())).isTrue();
    }

    @Test
    @DisplayName("assign same user twice — idempotent, no error, single thread row")
    void assignTicket_sameUserTwice_isIdempotent() {
        TicketDetailResponse ticket = createTicket();
        ticketService.assignTicket(ticket.getId(), assignRequest(staff1), admin.getId());

        assertThatCode(() -> ticketService
                .assignTicket(ticket.getId(), assignRequest(staff1), admin.getId()))
                .doesNotThrowAnyException();

        assertThat(subscriptionRepository.findParticipantUserIds("Ticket", ticket.getId())
                .stream().filter(staff1.getId()::equals)).hasSize(1);
    }

    // =========================================================================
    // C4 — status change to the thread, actor excluded
    // =========================================================================

    @Test
    @DisplayName("C4 updateStatus (assignee actor) — creator gets the VN transition row, actor excluded")
    void updateStatus_dispatchesToThreadMinusActor() {
        TicketDetailResponse ticket = createTicket();
        ticketService.assignTicket(ticket.getId(), assignRequest(staff1), admin.getId());

        ticketService.updateStatus(ticket.getId(), statusRequest(TicketStatus.IN_PROGRESS),
                staff1.getId(), "TECHNICIAN");

        String expectedBody = "Phản ánh \"" + ticket.getTitle()
                + "\" chuyển sang trạng thái: Đang xử lý.";
        List<Notification> rows = rowsWithBody(ticket.getId(), expectedBody);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUser().getId()).isEqualTo(resident.getId());
        assertThat(rows.get(0).getType()).isEqualTo(NotificationType.TICKET_STATUS_CHANGED);
    }

    // =========================================================================
    // C4 «Hoàn tất» + C5 rating request
    // =========================================================================

    @Test
    @DisplayName("C4+C5 DONE — thread row says «Hoàn tất» (locked term) and submitter gets TICKET_RATING_REQUESTED")
    void updateStatus_done_dispatchesHoanTatAndRatingRequest() {
        TicketDetailResponse ticket = createTicket();
        ticketService.assignTicket(ticket.getId(), assignRequest(staff1), admin.getId());
        ticketService.updateStatus(ticket.getId(), statusRequest(TicketStatus.IN_PROGRESS),
                staff1.getId(), "TECHNICIAN");

        ticketService.updateStatus(ticket.getId(), statusRequest(TicketStatus.DONE),
                admin.getId(), "ADMIN");

        // C4 — locked terminology: DONE renders as «Hoàn tất», never «Hoàn thành».
        String doneBody = "Phản ánh \"" + ticket.getTitle() + "\" chuyển sang trạng thái: Hoàn tất.";
        List<Notification> doneRows = rowsWithBody(ticket.getId(), doneBody);
        assertThat(doneRows)
                .extracting(row -> row.getUser().getId())
                .containsExactlyInAnyOrder(resident.getId(), staff1.getId());

        // C5 — submitter-only rating prompt with the dedicated type (G7).
        Notification ratingPrompt = rowFor(ticket.getId(),
                NotificationType.TICKET_RATING_REQUESTED, resident.getId());
        assertThat(ratingPrompt.getTitle()).isEqualTo("Đánh giá xử lý phản ánh");
        assertThat(ratingPrompt.getBody()).isEqualTo("Phản ánh \"" + ticket.getTitle()
                + "\" đã hoàn tất. Vui lòng đánh giá chất lượng xử lý.");
        assertThat(rowsFor(ticket.getId(), NotificationType.TICKET_RATING_REQUESTED)).hasSize(1);
    }

    // =========================================================================
    // C6 — rating to the assignee
    // =========================================================================

    @Test
    @DisplayName("C6 rate — assigned staff gets the TICKET_RATED row with the exact VN body")
    void rateTicket_dispatchesToAssignee() {
        TicketDetailResponse ticket = createTicket();
        ticketService.assignTicket(ticket.getId(), assignRequest(staff1), admin.getId());
        ticketService.updateStatus(ticket.getId(), statusRequest(TicketStatus.IN_PROGRESS),
                staff1.getId(), "TECHNICIAN");
        ticketService.updateStatus(ticket.getId(), statusRequest(TicketStatus.DONE),
                admin.getId(), "ADMIN");

        ticketService.rateTicket(ticket.getId(),
                RateTicketRequest.builder().rating(4).build(), resident.getId());

        Notification ratedRow = rowFor(ticket.getId(), NotificationType.TICKET_RATED, staff1.getId());
        assertThat(ratedRow.getTitle()).isEqualTo("Phản ánh được đánh giá");
        assertThat(ratedRow.getBody())
                .isEqualTo("Phản ánh \"" + ticket.getTitle() + "\" được cư dân đánh giá 4/5 sao.");
    }

    // =========================================================================
    // G4 — reassignment keeps the old assignee in the thread
    // =========================================================================

    @Test
    @DisplayName("G4 reassign — old assignee keeps the thread row and still receives C4")
    void reassign_keepsOldAssigneeInThread() {
        TicketDetailResponse ticket = createTicket();
        ticketService.assignTicket(ticket.getId(), assignRequest(staff1), admin.getId());
        ticketService.assignTicket(ticket.getId(), assignRequest(staff2), admin.getId());

        // Old assignee's subscription row survives the reassignment.
        assertThat(subscriptionRepository.existsByUserIdAndEntityTypeAndEntityId(
                staff1.getId(), "Ticket", ticket.getId())).isTrue();

        ticketService.updateStatus(ticket.getId(), statusRequest(TicketStatus.IN_PROGRESS),
                admin.getId(), "ADMIN");

        String expectedBody = "Phản ánh \"" + ticket.getTitle()
                + "\" chuyển sang trạng thái: Đang xử lý.";
        assertThat(rowsWithBody(ticket.getId(), expectedBody))
                .extracting(row -> row.getUser().getId())
                .containsExactlyInAnyOrder(resident.getId(), staff1.getId(), staff2.getId());
    }

    // =========================================================================
    // Fixture and query helpers
    // =========================================================================

    /**
     * Creates a ticket as the fixture resident through the service.
     *
     * @return the created ticket detail.
     */
    private TicketDetailResponse createTicket() {
        return ticketService.createTicket(createRequest(), resident.getId(), "RESIDENT");
    }

    /**
     * Builds a create request for the fixture apartment.
     *
     * @return the request.
     */
    private CreateTicketRequest createRequest() {
        return CreateTicketRequest.builder()
                .apartmentId(apartment.getId())
                .category(TicketCategory.MAINTENANCE_REPAIR)
                .title("N3P3 " + System.nanoTime())
                .description("Dispatch fixture")
                .build();
    }

    /**
     * Builds an assign request targeting the given staff user.
     *
     * @param staff the staff user.
     * @return the request.
     */
    private AssignTicketRequest assignRequest(User staff) {
        return AssignTicketRequest.builder().assignedToUserId(staff.getId()).build();
    }

    /**
     * Builds a status-update request.
     *
     * @param status the target status.
     * @return the request.
     */
    private UpdateTicketStatusRequest statusRequest(TicketStatus status) {
        return UpdateTicketStatusRequest.builder().status(status).build();
    }

    /**
     * Persists an active user with the given role and a unique "04"-prefixed phone.
     *
     * @param tag  uniqueness tag.
     * @param role the role to assign.
     * @return the saved user.
     */
    private User saveUser(long tag, UserRole role) {
        User user = new User();
        user.setPhone("04" + String.format("%08d", Math.abs(tag) % 100_000_000L));
        user.setFullName("P3 " + role + " " + tag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    /**
     * Lists all notification rows of one type referencing the ticket.
     *
     * @param ticketId the ticket UUID.
     * @param type     the notification type filter.
     * @return matching rows.
     */
    private List<Notification> rowsFor(UUID ticketId, NotificationType type) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id AND n.type = :type",
                        Notification.class)
                .setParameter("id", ticketId)
                .setParameter("type", type)
                .getResultList();
    }

    /**
     * Loads the single row of one type for one user referencing the ticket.
     *
     * @param ticketId the ticket UUID.
     * @param type     the notification type.
     * @param userId   the recipient user UUID.
     * @return the matching row.
     */
    private Notification rowFor(UUID ticketId, NotificationType type, UUID userId) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id"
                                + " AND n.type = :type AND n.user.id = :userId",
                        Notification.class)
                .setParameter("id", ticketId)
                .setParameter("type", type)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    /**
     * Lists all rows referencing the ticket with an exact body match.
     *
     * @param ticketId the ticket UUID.
     * @param body     the exact body string.
     * @return matching rows.
     */
    private List<Notification> rowsWithBody(UUID ticketId, String body) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id AND n.body = :body",
                        Notification.class)
                .setParameter("id", ticketId)
                .setParameter("body", body)
                .getResultList();
    }

    /**
     * Lists all notification rows referencing the ticket addressed to one user.
     *
     * @param ticketId the ticket UUID.
     * @param userId   the user UUID.
     * @return matching rows.
     */
    private List<Notification> allRowsForUser(UUID ticketId, UUID userId) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id AND n.user.id = :userId",
                        Notification.class)
                .setParameter("id", ticketId)
                .setParameter("userId", userId)
                .getResultList();
    }
}
