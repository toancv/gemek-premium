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
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.notification.entity.NotificationType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled job that checks for contracts expiring within the next 30 days.
 *
 * <p>Runs daily at 08:00. Creates a {@link NotificationType#CONTRACT_EXPIRING}
 * in-app notification for the staff member ({@code createdBy}) linked to each
 * expiring contract. Contracts without a {@code createdBy} user are skipped —
 * they may have been created by a deleted user or a data-migration script.
 *
 * <p>Once-only (G6 fix): the scan excludes contracts whose
 * {@code expiry_notified_at} sent-marker is set, and the marker is written on the
 * managed entity in the same transaction as the notification insert — each
 * expiring contract notifies exactly once instead of daily for 30 days.
 */
@Component
public class ContractExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContractExpiryScheduler.class);

    /** Number of days ahead to look for expiring contracts. */
    private static final int LOOK_AHEAD_DAYS = 30;

    private final ContractRepository contractRepository;
    private final NotificationService notificationService;

    /**
     * Constructs the scheduler with all required dependencies.
     *
     * @param contractRepository  the contract JPA repository.
     * @param notificationService the notification service used to create alert records.
     */
    public ContractExpiryScheduler(ContractRepository contractRepository,
                                   NotificationService notificationService) {
        this.contractRepository = contractRepository;
        this.notificationService = notificationService;
    }

    /**
     * Finds all ACTIVE contracts whose end date falls within the next 30 days and
     * which have not been notified yet, logs the count, and creates a notification
     * for each contract's assigned staff user.
     *
     * <p>Transactional so the {@code expiryNotifiedAt} sent-marker (set only after a
     * successful notification insert) flushes atomically with the notification row —
     * a crash mid-run leaves neither, never one without the other.
     *
     * <p>Cron expression: run at 08:00 every day.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkExpiringContracts() {
        LocalDate today = LocalDate.now();
        List<Contract> expiring = contractRepository.findExpiringBetween(today, today.plusDays(LOOK_AHEAD_DAYS));
        log.info("Contract expiry check: {} contracts expiring within 30 days.", expiring.size());

        for (Contract contract : expiring) {
            // Skip contracts with no linked staff user — nothing to notify.
            if (contract.getCreatedBy() == null) {
                log.debug("Contract {} has no createdBy user — skipping notification.", contract.getId());
                continue;
            }
            try {
                notificationService.createNotification(
                        contract.getCreatedBy(),
                        "Contract expiring soon",
                        "Contract \"" + contract.getTitle() + "\" expires on " + contract.getEndDate() + ".",
                        NotificationType.CONTRACT_EXPIRING,
                        contract.getId(),
                        "Contract"
                );
                // G6 sent-marker — only after a successful insert, same transaction.
                contract.setExpiryNotifiedAt(OffsetDateTime.now());
            } catch (Exception notifyException) {
                // Notification failure must not abort the scheduler run for other contracts.
                log.warn("Failed to create expiry notification for contract {}: {}",
                        contract.getId(), notifyException.getMessage());
            }
        }
    }
}
