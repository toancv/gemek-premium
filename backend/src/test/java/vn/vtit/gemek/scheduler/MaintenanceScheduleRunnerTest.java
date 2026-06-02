/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule;
import vn.vtit.gemek.module.contractor.repository.MaintenanceScheduleRepository;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.user.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MaintenanceScheduleRunner} — GAP-09 scheduler branch coverage.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleRunnerTest {

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private NotificationService notificationService;

    private MaintenanceScheduleRunner runner;

    @BeforeEach
    void setUp() {
        runner = new MaintenanceScheduleRunner(scheduleRepository, notificationService);
    }

    @Test
    @DisplayName("checkOverdueSchedules: no overdue schedules — notificationService not called")
    void checkOverdueSchedules_noOverdue_notificationNotCalled() {
        when(scheduleRepository.findOverdue(any(LocalDate.class))).thenReturn(List.of());

        runner.checkOverdueSchedules();

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("checkOverdueSchedules: overdue schedule with user — notification created")
    void checkOverdueSchedules_overdueWithUser_notificationCreated() {
        UUID staffId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        User staff = new User();
        staff.setId(staffId);

        Contract contract = new Contract();
        contract.setCreatedBy(staff);

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setId(scheduleId);
        schedule.setTitle("HVAC Service");
        schedule.setNextDueDate(LocalDate.now().minusDays(1));
        schedule.setContract(contract);

        when(scheduleRepository.findOverdue(any(LocalDate.class))).thenReturn(List.of(schedule));

        runner.checkOverdueSchedules();

        verify(notificationService).createNotification(
                eq(staffId), any(), any(), eq(NotificationType.SCHEDULE_DUE), eq(scheduleId), eq("MaintenanceSchedule"));
    }

    @Test
    @DisplayName("checkOverdueSchedules: schedule with null contract — notification skipped")
    void checkOverdueSchedules_nullContract_notificationSkipped() {
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setContract(null);

        when(scheduleRepository.findOverdue(any(LocalDate.class))).thenReturn(List.of(schedule));

        runner.checkOverdueSchedules();

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("checkOverdueSchedules: schedule with null createdBy — notification skipped")
    void checkOverdueSchedules_nullCreatedBy_notificationSkipped() {
        Contract contract = new Contract();
        contract.setCreatedBy(null);

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setContract(contract);

        when(scheduleRepository.findOverdue(any(LocalDate.class))).thenReturn(List.of(schedule));

        runner.checkOverdueSchedules();

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("checkOverdueSchedules: notification throws — does not abort processing of next schedule")
    void checkOverdueSchedules_notificationThrows_continuesProcessing() {
        UUID staffId1 = UUID.randomUUID();
        UUID staffId2 = UUID.randomUUID();

        User staff1 = new User();
        staff1.setId(staffId1);
        User staff2 = new User();
        staff2.setId(staffId2);

        Contract contract1 = new Contract();
        contract1.setCreatedBy(staff1);
        Contract contract2 = new Contract();
        contract2.setCreatedBy(staff2);

        MaintenanceSchedule s1 = new MaintenanceSchedule();
        s1.setId(UUID.randomUUID());
        s1.setTitle("S1");
        s1.setNextDueDate(LocalDate.now());
        s1.setContract(contract1);

        MaintenanceSchedule s2 = new MaintenanceSchedule();
        s2.setId(UUID.randomUUID());
        s2.setTitle("S2");
        s2.setNextDueDate(LocalDate.now());
        s2.setContract(contract2);

        when(scheduleRepository.findOverdue(any(LocalDate.class))).thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("Redis down"))
                .when(notificationService).createNotification(eq(staffId1), any(), any(), any(), any(), any());

        runner.checkOverdueSchedules();

        // s2 must still be notified despite s1 throwing
        verify(notificationService).createNotification(
                eq(staffId2), any(), any(), eq(NotificationType.SCHEDULE_DUE), any(), any());
    }
}
