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
import vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule;
import vn.vtit.gemek.module.contractor.repository.MaintenanceScheduleRepository;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.notification.entity.NotificationType;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that checks for overdue maintenance schedules.
 *
 * <p>Runs daily at 07:30. Creates a {@link NotificationType#SCHEDULE_DUE}
 * in-app notification for each overdue schedule, addressed to the staff member
 * ({@code contract.createdBy}) responsible for the linked contract.
 *
 * <p>The scheduled method runs inside a read-only transaction so that lazy
 * associations on {@link MaintenanceSchedule} (e.g., {@code contract.createdBy})
 * can be traversed without triggering a {@code LazyInitializationException}.
 * Notification creation opens its own write transaction internally.
 */
@Component
public class MaintenanceScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduleRunner.class);

    private final MaintenanceScheduleRepository scheduleRepository;
    private final NotificationService notificationService;

    /**
     * Constructs the runner with all required dependencies.
     *
     * @param scheduleRepository  the maintenance schedule JPA repository.
     * @param notificationService the notification service used to create alert records.
     */
    public MaintenanceScheduleRunner(MaintenanceScheduleRepository scheduleRepository,
                                     NotificationService notificationService) {
        this.scheduleRepository = scheduleRepository;
        this.notificationService = notificationService;
    }

    /**
     * Finds all active maintenance schedules whose {@code next_due_date} is today or earlier,
     * logs the count, and creates a notification for each schedule's responsible staff user.
     *
     * <p>The read-only transaction keeps the JPA session open so that lazy associations
     * on the fetched {@link MaintenanceSchedule} entities can be accessed safely.
     *
     * <p>Cron expression: run at 07:30 every day.
     */
    @Scheduled(cron = "0 30 7 * * *")
    @Transactional(readOnly = true)
    public void checkOverdueSchedules() {
        List<MaintenanceSchedule> overdue = scheduleRepository.findOverdue(LocalDate.now());
        log.info("Maintenance schedule check: {} overdue schedules.", overdue.size());

        for (MaintenanceSchedule schedule : overdue) {
            // Navigate the lazy contract association; createdBy is the responsible user's actor UUID.
            if (schedule.getContract() == null || schedule.getContract().getCreatedBy() == null) {
                log.debug("Schedule {} has no associated user — skipping notification.", schedule.getId());
                continue;
            }
            try {
                notificationService.createNotification(
                        schedule.getContract().getCreatedBy(),
                        "Maintenance task overdue",
                        "Maintenance task \"" + schedule.getTitle()
                                + "\" was due on " + schedule.getNextDueDate() + " and has not been completed.",
                        NotificationType.SCHEDULE_DUE,
                        schedule.getId(),
                        "MaintenanceSchedule"
                );
            } catch (Exception notifyException) {
                // Notification failure must not abort the scheduler run for other schedules.
                log.warn("Failed to create overdue notification for schedule {}: {}",
                        schedule.getId(), notifyException.getMessage());
            }
        }
    }
}
