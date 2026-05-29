/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import vn.vtit.gemek.module.contractor.entity.ScheduleFrequency;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only response DTO for a {@link vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule}.
 *
 * @param id           the schedule UUID.
 * @param contract     slim contract reference (id + title).
 * @param title        label for the recurring task.
 * @param frequency    how often the task recurs.
 * @param nextDueDate  next date by which the task must be performed.
 * @param lastDoneDate date the task was last completed; {@code null} if never performed.
 * @param notes        optional notes or checklist items.
 * @param active       whether the schedule is currently active.
 * @param createdAt    record creation timestamp.
 */
public record MaintenanceScheduleResponse(
        UUID id,
        ContractRef contract,
        String title,
        ScheduleFrequency frequency,
        LocalDate nextDueDate,
        LocalDate lastDoneDate,
        String notes,
        boolean active,
        OffsetDateTime createdAt
) {

    /**
     * Slim contract reference embedded in schedule responses.
     *
     * @param id    the contract UUID.
     * @param title the contract title.
     */
    public record ContractRef(UUID id, String title) {}
}
