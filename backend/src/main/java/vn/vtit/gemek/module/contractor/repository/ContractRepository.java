/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.contractor.entity.Contract;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Contract} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} for future dynamic filter support.
 * The {@link #findExpiringBetween} query is used by {@link vn.vtit.gemek.scheduler.ContractExpiryScheduler}
 * to identify contracts expiring within a configurable look-ahead window.
 */
@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {

    /**
     * Returns all contracts belonging to the given contractor, ordered by start date descending.
     *
     * @param contractorId the contractor UUID.
     * @return list of contracts for that contractor.
     */
    List<Contract> findByContractorId(UUID contractorId);

    /**
     * Returns all ACTIVE contracts whose end date falls within the given date range.
     *
     * <p>Used by the daily expiry scheduler to find contracts expiring within 30 days.
     *
     * @param from the start of the look-ahead window (inclusive).
     * @param to   the end of the look-ahead window (inclusive).
     * @return list of expiring contracts.
     */
    @Query("SELECT c FROM Contract c WHERE c.status = 'ACTIVE' AND c.endDate BETWEEN :from AND :to")
    List<Contract> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
