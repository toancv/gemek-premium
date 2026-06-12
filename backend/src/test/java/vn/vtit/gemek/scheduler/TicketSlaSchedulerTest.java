/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.scheduler;

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
import vn.vtit.gemek.module.ticket.entity.Ticket;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.ticket.entity.TicketStatus;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N3 P6 — deterministic SLA scheduler tests (design §D).
 *
 * <p>Invokes {@link TicketSlaScheduler#checkSlaDeadlines()} directly — no cron
 * waits, no sleeps. Covers: warning-window dispatch + idempotency (second run = 0
 * new rows), overdue dispatch + idempotency, the already-overdue-at-first-scan
 * edge (ONLY the breach is sent, BOTH markers set), terminal-status and
 * null-deadline exclusion, exact VN strings including the +07 deadline rendering,
 * and assignee/admin recipient dedup.
 *
 * <p>Class-level {@code @Transactional} rolls all fixtures back. Assertions are
 * scoped per-ticket ({@code referenceId}) — the shared dev DB contains foreign rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketSlaSchedulerTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private TicketSlaScheduler scheduler;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Apartment apartment;
    private User resident;
    private User admin;
    private User staff;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        Block block = new Block();
        block.setName("P6-" + tag);
        block = blockRepository.save(block);

        apartment = new Apartment();
        apartment.setBlock(block);
        apartment.setFloor((short) 1);
        apartment.setUnitNumber("P6-" + tag);
        apartment = apartmentRepository.save(apartment);

        // Phone prefix "02" — unused by other test classes (04/05/06/07/08 taken).
        resident = saveUser(tag + 1, UserRole.RESIDENT);
        admin = saveUser(tag + 2, UserRole.ADMIN);
        staff = saveUser(tag + 3, UserRole.TECHNICIAN);
    }

    // =========================================================================
    // C7 — warning window
    // =========================================================================

    @Test
    @DisplayName("warning window — one TICKET_SLA_WARNING per recipient, marker set; second run adds zero rows")
    void warningWindow_dispatchesOncePerRecipient_secondRunZero() {
        Ticket ticket = saveTicket(OffsetDateTime.now().plusHours(1), TicketStatus.IN_PROGRESS, staff);

        scheduler.checkSlaDeadlines();

        Set<UUID> expectedRecipients = expectedRecipients(staff);
        List<Notification> rows = rowsFor(ticket.getId(), NotificationType.TICKET_SLA_WARNING);
        assertThat(rows).extracting(row -> row.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(expectedRecipients);
        assertThat(ticket.getSlaWarningNotifiedAt()).isNotNull();
        assertThat(ticket.getSlaOverdueNotifiedAt()).isNull();

        // Idempotency: immediate second invocation must add nothing.
        scheduler.checkSlaDeadlines();
        assertThat(rowsFor(ticket.getId(), NotificationType.TICKET_SLA_WARNING))
                .hasSize(expectedRecipients.size());
    }

    @Test
    @DisplayName("warning body — exact VN string with deadline rendered in Asia/Ho_Chi_Minh (+07), not UTC")
    void warningBody_exactVnString_renderedInVietnamTime() {
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(90);
        Ticket ticket = saveTicket(deadline, TicketStatus.NEW, staff);

        scheduler.checkSlaDeadlines();

        // Expected rendering computed independently with a literal +07 offset
        // (VN has no DST) — catches any UTC cast artifact.
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        String deadlineVn = deadline.atZoneSameInstant(ZoneOffset.ofHours(7)).format(format);
        String deadlineUtc = deadline.atZoneSameInstant(ZoneOffset.UTC).format(format);
        assertThat(deadlineVn).isNotEqualTo(deadlineUtc);

        Notification row = rowsFor(ticket.getId(), NotificationType.TICKET_SLA_WARNING).get(0);
        assertThat(row.getTitle()).isEqualTo("Phản ánh sắp quá hạn");
        assertThat(row.getBody()).isEqualTo(
                "Phản ánh \"" + ticket.getTitle() + "\" sắp đến hạn xử lý (" + deadlineVn + ").");
        assertThat(row.getReferenceId()).isEqualTo(ticket.getId());
        assertThat(row.getReferenceType()).isEqualTo("Ticket");
    }

    // =========================================================================
    // C8 — overdue
    // =========================================================================

    @Test
    @DisplayName("overdue — one TICKET_SLA_BREACHED per recipient with exact VN body, marker set; second run adds zero rows")
    void overdue_dispatchesBreachOnce_secondRunZero() {
        Ticket ticket = saveTicket(OffsetDateTime.now().minusHours(1), TicketStatus.IN_PROGRESS, staff);

        scheduler.checkSlaDeadlines();

        Set<UUID> expectedRecipients = expectedRecipients(staff);
        List<Notification> rows = rowsFor(ticket.getId(), NotificationType.TICKET_SLA_BREACHED);
        assertThat(rows).extracting(row -> row.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(expectedRecipients);
        assertThat(rows.get(0).getTitle()).isEqualTo("Phản ánh quá hạn");
        assertThat(rows.get(0).getBody())
                .isEqualTo("Phản ánh \"" + ticket.getTitle() + "\" đã quá hạn xử lý.");
        assertThat(ticket.getSlaOverdueNotifiedAt()).isNotNull();

        // Idempotency: immediate second invocation must add nothing.
        scheduler.checkSlaDeadlines();
        assertThat(rowsFor(ticket.getId(), NotificationType.TICKET_SLA_BREACHED))
                .hasSize(expectedRecipients.size());
    }

    @Test
    @DisplayName("already overdue at first scan — ONLY the breach is sent, BOTH markers set")
    void alreadyOverdueAtFirstScan_sendsOnlyBreach_setsBothMarkers() {
        Ticket ticket = saveTicket(OffsetDateTime.now().minusMinutes(30), TicketStatus.NEW, null);

        scheduler.checkSlaDeadlines();

        assertThat(rowsFor(ticket.getId(), NotificationType.TICKET_SLA_WARNING)).isEmpty();
        assertThat(rowsFor(ticket.getId(), NotificationType.TICKET_SLA_BREACHED)).isNotEmpty();
        assertThat(ticket.getSlaWarningNotifiedAt()).isNotNull();
        assertThat(ticket.getSlaOverdueNotifiedAt()).isNotNull();
    }

    // =========================================================================
    // Exclusions
    // =========================================================================

    @Test
    @DisplayName("DONE and CANCELLED tickets inside the window — zero rows, markers untouched")
    void terminalStatusInsideWindow_zeroRows() {
        Ticket done = saveTicket(OffsetDateTime.now().plusHours(1), TicketStatus.DONE, staff);
        Ticket cancelled = saveTicket(OffsetDateTime.now().minusHours(1), TicketStatus.CANCELLED, staff);

        scheduler.checkSlaDeadlines();

        assertThat(allRows(done.getId())).isEmpty();
        assertThat(allRows(cancelled.getId())).isEmpty();
        assertThat(done.getSlaWarningNotifiedAt()).isNull();
        assertThat(cancelled.getSlaOverdueNotifiedAt()).isNull();
    }

    @Test
    @DisplayName("SUGGESTION_FEEDBACK (null slaDeadline) — untouched")
    void nullSlaDeadline_untouched() {
        Ticket suggestion = saveTicket(null, TicketStatus.NEW, null);

        scheduler.checkSlaDeadlines();

        assertThat(allRows(suggestion.getId())).isEmpty();
        assertThat(suggestion.getSlaWarningNotifiedAt()).isNull();
        assertThat(suggestion.getSlaOverdueNotifiedAt()).isNull();
    }

    // =========================================================================
    // Recipient dedup
    // =========================================================================

    @Test
    @DisplayName("assignee who is an admin — deduplicated, exactly one row per active admin")
    void adminAssignee_isDeduplicated() {
        Ticket ticket = saveTicket(OffsetDateTime.now().plusHours(1), TicketStatus.IN_PROGRESS, admin);

        scheduler.checkSlaDeadlines();

        List<UUID> adminIds = userRepository.findActiveUserIdsByRole(UserRole.ADMIN);
        assertThat(rowsFor(ticket.getId(), NotificationType.TICKET_SLA_WARNING))
                .extracting(row -> row.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(adminIds);
    }

    // =========================================================================
    // Fixture and query helpers
    // =========================================================================

    /**
     * Persists a ticket directly through the repository with a controlled deadline.
     *
     * <p>Bypasses the service so the SLA deadline can sit inside/outside the warning
     * window at will (the service always computes it 24–72h ahead). A {@code null}
     * deadline models SUGGESTION_FEEDBACK, which carries no SLA.
     *
     * @param slaDeadline the deadline to set; {@code null} for no SLA.
     * @param status      the ticket status.
     * @param assignee    the assigned staff user; {@code null} for unassigned.
     * @return the saved ticket.
     */
    private Ticket saveTicket(OffsetDateTime slaDeadline, TicketStatus status, User assignee) {
        Ticket ticket = new Ticket();
        ticket.setApartment(apartment);
        ticket.setSubmittedBy(resident);
        ticket.setCategory(slaDeadline == null
                ? TicketCategory.SUGGESTION_FEEDBACK : TicketCategory.MAINTENANCE_REPAIR);
        ticket.setTitle("N3P6 " + System.nanoTime());
        ticket.setStatus(status);
        ticket.setSlaDeadline(slaDeadline);
        ticket.setAssignedToUser(assignee);
        return ticketRepository.save(ticket);
    }

    /**
     * Computes the expected recipient set: assignee plus all active admins, deduplicated.
     *
     * @param assignee the assigned staff user.
     * @return the expected recipient user IDs.
     */
    private Set<UUID> expectedRecipients(User assignee) {
        Set<UUID> expected = new LinkedHashSet<>();
        expected.add(assignee.getId());
        expected.addAll(userRepository.findActiveUserIdsByRole(UserRole.ADMIN));
        return expected;
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
        user.setFullName("P6 " + role + " " + tag);
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
     * Lists all notification rows referencing the ticket, any type.
     *
     * @param ticketId the ticket UUID.
     * @return matching rows.
     */
    private List<Notification> allRows(UUID ticketId) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id",
                        Notification.class)
                .setParameter("id", ticketId)
                .getResultList();
    }
}
