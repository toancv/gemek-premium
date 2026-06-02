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
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
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
 * Unit tests for {@link ContractExpiryScheduler} — GAP-09 scheduler branch coverage.
 */
@ExtendWith(MockitoExtension.class)
class ContractExpirySchedulerTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private NotificationService notificationService;

    private ContractExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ContractExpiryScheduler(contractRepository, notificationService);
    }

    @Test
    @DisplayName("checkExpiringContracts: no expiring contracts — notificationService not called")
    void checkExpiringContracts_noExpiring_notificationNotCalled() {
        when(contractRepository.findExpiringBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.checkExpiringContracts();

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("checkExpiringContracts: expiring contract with user — notification created")
    void checkExpiringContracts_expiringWithUser_notificationCreated() {
        UUID staffId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();

        User staff = new User();
        staff.setId(staffId);

        Contract contract = new Contract();
        contract.setId(contractId);
        contract.setTitle("Cleaning Contract");
        contract.setEndDate(LocalDate.now().plusDays(15));
        contract.setCreatedBy(staff);

        when(contractRepository.findExpiringBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(contract));

        scheduler.checkExpiringContracts();

        verify(notificationService).createNotification(
                eq(staffId), any(), any(), eq(NotificationType.CONTRACT_EXPIRING),
                eq(contractId), eq("Contract"));
    }

    @Test
    @DisplayName("checkExpiringContracts: contract with null createdBy — notification skipped")
    void checkExpiringContracts_nullCreatedBy_notificationSkipped() {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setCreatedBy(null);

        when(contractRepository.findExpiringBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(contract));

        scheduler.checkExpiringContracts();

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("checkExpiringContracts: notification throws — continues to next contract")
    void checkExpiringContracts_notificationThrows_continuesProcessing() {
        UUID staffId1 = UUID.randomUUID();
        UUID staffId2 = UUID.randomUUID();

        User staff1 = new User();
        staff1.setId(staffId1);
        User staff2 = new User();
        staff2.setId(staffId2);

        Contract c1 = new Contract();
        c1.setId(UUID.randomUUID());
        c1.setTitle("C1");
        c1.setEndDate(LocalDate.now().plusDays(5));
        c1.setCreatedBy(staff1);

        Contract c2 = new Contract();
        c2.setId(UUID.randomUUID());
        c2.setTitle("C2");
        c2.setEndDate(LocalDate.now().plusDays(10));
        c2.setCreatedBy(staff2);

        when(contractRepository.findExpiringBetween(any(), any())).thenReturn(List.of(c1, c2));
        doThrow(new RuntimeException("down"))
                .when(notificationService).createNotification(eq(staffId1), any(), any(), any(), any(), any());

        scheduler.checkExpiringContracts();

        verify(notificationService).createNotification(
                eq(staffId2), any(), any(), eq(NotificationType.CONTRACT_EXPIRING), any(), any());
    }
}
