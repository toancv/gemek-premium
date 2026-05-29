/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.notification.NotificationService;
import vn.vtit.gemek.module.notification.entity.NotificationType;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that checks for contracts expiring within the next 30 days.
 *
 * <p>Runs daily at 08:00. Creates a {@link NotificationType#CONTRACT_EXPIRING}
 * in-app notification for the staff member ({@code createdBy}) linked to each
 * expiring contract. Contracts without a {@code createdBy} user are skipped —
 * they may have been created by a deleted user or a data-migration script.
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
     * Finds all ACTIVE contracts whose end date falls within the next 30 days,
     * logs the count, and creates a notification for each contract's assigned staff user.
     *
     * <p>Cron expression: run at 08:00 every day.
     */
    @Scheduled(cron = "0 0 8 * * *")
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
                        contract.getCreatedBy().getId(),
                        "Contract expiring soon",
                        "Contract \"" + contract.getTitle() + "\" expires on " + contract.getEndDate() + ".",
                        NotificationType.CONTRACT_EXPIRING,
                        contract.getId(),
                        "Contract"
                );
            } catch (Exception notifyException) {
                // Notification failure must not abort the scheduler run for other contracts.
                log.warn("Failed to create expiry notification for contract {}: {}",
                        contract.getId(), notifyException.getMessage());
            }
        }
    }
}
