/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link MaintenanceSchedule} entity.
 *
 * <p>The {@link #findOverdue} query is used by
 * {@link vn.vtit.gemek.scheduler.MaintenanceScheduleRunner} to surface tasks
 * whose {@code next_due_date} has passed without being completed.
 */
@Repository
public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, UUID> {

    /**
     * Returns all schedules linked to the given contract.
     *
     * @param contractId the contract UUID.
     * @return list of maintenance schedules for that contract.
     */
    List<MaintenanceSchedule> findByContractId(UUID contractId);

    /**
     * Returns all active schedules whose {@code next_due_date} is on or before today.
     *
     * @param today the reference date (typically {@link LocalDate#now()}).
     * @return list of overdue active maintenance schedules.
     */
    @Query("SELECT s FROM MaintenanceSchedule s WHERE s.active = true AND s.nextDueDate <= :today")
    List<MaintenanceSchedule> findOverdue(@Param("today") LocalDate today);
}
