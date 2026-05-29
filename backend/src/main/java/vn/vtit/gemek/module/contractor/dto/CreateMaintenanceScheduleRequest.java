/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.vtit.gemek.module.contractor.entity.ScheduleFrequency;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for adding a maintenance schedule to a contract.
 *
 * @param contractId  the contract UUID the schedule belongs to (required).
 * @param title       label for the recurring task (required).
 * @param frequency   how often the task recurs (required).
 * @param nextDueDate next date by which the task must be performed (required).
 * @param notes       optional notes or checklist items.
 */
public record CreateMaintenanceScheduleRequest(
        @NotNull UUID contractId,
        @NotBlank String title,
        @NotNull ScheduleFrequency frequency,
        @NotNull LocalDate nextDueDate,
        String notes
) {}
