/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.ticket.entity.Ticket;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled job dispatching SLA warning and breach notifications for tickets (N3 P6, design §D).
 *
 * <p>Runs every 15 minutes. Two scans against {@code tickets}:
 * <ul>
 *   <li><b>Overdue</b> — deadline passed, {@code sla_overdue_notified_at IS NULL} →
 *       one {@link NotificationType#TICKET_SLA_BREACHED} per recipient.</li>
 *   <li><b>Warning</b> — deadline within the next {@value #WARNING_LEAD_HOURS} hours
 *       (G2: fixed for all categories), {@code sla_warning_notified_at IS NULL} →
 *       one {@link NotificationType#TICKET_SLA_WARNING} per recipient.</li>
 * </ul>
 *
 * <p>Recipients per ticket (G5): the assigned staff user if any, plus all active
 * ADMIN users, deduplicated. Idempotency: the sent-marker columns are set on the
 * managed entities inside the same transaction as the notification inserts — a
 * crash rolls back both, a re-scan can never re-notify. A ticket first seen
 * already overdue receives ONLY the breach notification and has BOTH markers set
 * (the overdue scan runs first; the warning scan's {@code slaDeadline >= now}
 * lower bound excludes overdue tickets).
 *
 * <p>ShedLock is intentionally absent: deployment is a single backend container
 * (documented assumption shared with the two sibling schedulers).
 */
@Component
public class TicketSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(TicketSlaScheduler.class);

    /** Fixed warning lead time before the SLA deadline, in hours (G2 ruling). */
    static final int WARNING_LEAD_HOURS = 2;

    /** Ticket reference type label shared with the ticket dispatch path. */
    private static final String TICKET_REFERENCE_TYPE = "Ticket";

    /**
     * Display zone for deadline rendering in notification bodies. Deadlines are
     * stored as TIMESTAMPTZ; rendering MUST convert to Vietnam local time, never UTC.
     */
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Deadline display format used in the warning body (design §C C7). */
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Constructs the scheduler with all required dependencies.
     *
     * @param ticketRepository       the ticket JPA repository providing the scan queries.
     * @param userRepository         the user repository for admin-recipient resolution and FK references.
     * @param notificationRepository the notification repository for batched inserts.
     */
    public TicketSlaScheduler(TicketRepository ticketRepository,
                              UserRepository userRepository,
                              NotificationRepository notificationRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Scans for tickets past or approaching their SLA deadline and dispatches the
     * corresponding notifications exactly once per ticket and event kind.
     *
     * <p>Overdue scan runs first so that a ticket first seen already overdue gets
     * only the breach notification with both markers set; the warning scan then
     * cannot match it. Both scans share one {@code now} snapshot — no gap between
     * the two predicates within a run.
     *
     * <p>Cron expression: every 15 minutes.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void checkSlaDeadlines() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> adminIds = userRepository.findActiveUserIdsByRole(UserRole.ADMIN);

        List<Ticket> overdue = ticketRepository.findSlaOverdueCandidates(now);
        // Overdue first: send the breach row, set BOTH markers (a warning after the
        // breach is pointless — design §D both-match edge).
        for (Ticket ticket : overdue) {
            dispatch(ticket, adminIds,
                    "Phản ánh quá hạn",
                    "Phản ánh \"" + ticket.getTitle() + "\" đã quá hạn xử lý.",
                    NotificationType.TICKET_SLA_BREACHED);
            ticket.setSlaOverdueNotifiedAt(now);
            // Suppress the now-pointless warning for tickets first seen overdue.
            if (ticket.getSlaWarningNotifiedAt() == null) {
                ticket.setSlaWarningNotifiedAt(now);
            }
        }

        List<Ticket> approaching = ticketRepository.findSlaWarningCandidates(
                now, now.plusHours(WARNING_LEAD_HOURS));
        // Warning scan: deadline still ahead but inside the fixed 2-hour window.
        for (Ticket ticket : approaching) {
            String deadlineVn = ticket.getSlaDeadline().atZoneSameInstant(VN_ZONE).format(DEADLINE_FORMAT);
            dispatch(ticket, adminIds,
                    "Phản ánh sắp quá hạn",
                    "Phản ánh \"" + ticket.getTitle() + "\" sắp đến hạn xử lý (" + deadlineVn + ").",
                    NotificationType.TICKET_SLA_WARNING);
            ticket.setSlaWarningNotifiedAt(now);
        }

        log.info("SLA scan: {} overdue, {} approaching tickets notified.",
                overdue.size(), approaching.size());
    }

    /**
     * Creates one notification row per recipient for a ticket in a single batched insert.
     *
     * <p>Recipients = assignee (if any) + active ADMINs, deduplicated via an ordered
     * set (G5). Same batch pattern as {@code TicketServiceImpl.dispatchTicketNotifications}:
     * user FKs via {@code getReferenceById}, one {@code saveAll}, no logging in the loop.
     *
     * @param ticket   the ticket the event refers to.
     * @param adminIds the active ADMIN user IDs (resolved once per run).
     * @param title    VN notification title.
     * @param body     VN notification body.
     * @param type     the notification type.
     */
    private void dispatch(Ticket ticket, List<UUID> adminIds, String title, String body,
                          NotificationType type) {
        Set<UUID> recipients = new LinkedHashSet<>();
        // Assignee first (if staff is assigned); admins appended — set dedups an admin assignee.
        if (ticket.getAssignedToUser() != null) {
            recipients.add(ticket.getAssignedToUser().getId());
        }
        recipients.addAll(adminIds);
        if (recipients.isEmpty()) {
            return;
        }

        List<Notification> batch = new ArrayList<>(recipients.size());
        // Build the full batch in memory, then one saveAll.
        for (UUID userId : recipients) {
            Notification notification = new Notification();
            notification.setUser(userRepository.getReferenceById(userId));
            notification.setTitle(title);
            notification.setBody(body);
            notification.setType(type);
            notification.setReferenceId(ticket.getId());
            notification.setReferenceType(TICKET_REFERENCE_TYPE);
            batch.add(notification);
        }
        notificationRepository.saveAll(batch);
    }
}
