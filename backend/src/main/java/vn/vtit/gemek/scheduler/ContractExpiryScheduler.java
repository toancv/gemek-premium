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

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that checks for contracts expiring within the next 30 days.
 *
 * <p>Runs daily at 08:00. In Module 10 this stub will be extended to create
 * in-app notification records for each expiring contract.
 */
@Component
public class ContractExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContractExpiryScheduler.class);

    /** Number of days ahead to look for expiring contracts. */
    private static final int LOOK_AHEAD_DAYS = 30;

    private final ContractRepository contractRepository;

    /**
     * Constructs the scheduler with the required contract repository dependency.
     *
     * @param contractRepository the contract JPA repository.
     */
    public ContractExpiryScheduler(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /**
     * Finds all ACTIVE contracts whose end date falls within the next 30 days and
     * logs the count. Notification delivery is wired in Module 10.
     *
     * <p>Cron expression: run at 08:00 every day.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void checkExpiringContracts() {
        LocalDate today = LocalDate.now();
        List<Contract> expiring = contractRepository.findExpiringBetween(today, today.plusDays(LOOK_AHEAD_DAYS));
        log.info("Contract expiry check: {} contracts expiring within 30 days.", expiring.size());
        // TODO Module 10: create notification records for each expiring contract.
    }
}
