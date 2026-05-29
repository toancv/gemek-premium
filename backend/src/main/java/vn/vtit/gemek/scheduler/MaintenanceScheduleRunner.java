/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule;
import vn.vtit.gemek.module.contractor.repository.MaintenanceScheduleRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that checks for overdue maintenance schedules.
 *
 * <p>Runs daily at 07:30. In Module 10 this stub will be extended to create
 * in-app notification records for each overdue schedule.
 */
@Component
public class MaintenanceScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduleRunner.class);

    private final MaintenanceScheduleRepository scheduleRepository;

    /**
     * Constructs the runner with the required maintenance schedule repository dependency.
     *
     * @param scheduleRepository the maintenance schedule JPA repository.
     */
    public MaintenanceScheduleRunner(MaintenanceScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Finds all active maintenance schedules whose {@code next_due_date} is today or earlier
     * and logs the count. Notification delivery is wired in Module 10.
     *
     * <p>Cron expression: run at 07:30 every day.
     */
    @Scheduled(cron = "0 30 7 * * *")
    public void checkOverdueSchedules() {
        List<MaintenanceSchedule> overdue = scheduleRepository.findOverdue(LocalDate.now());
        log.info("Maintenance schedule check: {} overdue schedules.", overdue.size());
        // TODO Module 10: create notification records for each overdue schedule.
    }
}
