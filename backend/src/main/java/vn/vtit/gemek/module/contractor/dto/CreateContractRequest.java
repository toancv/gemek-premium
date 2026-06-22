/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating a new contract under a contractor.
 *
 * @param contractorId  the contractor UUID (required).
 * @param title         short descriptive title (required).
 * @param scope         detailed scope of work.
 * @param contractValue total monetary value.
 * @param currency      ISO 4217 currency code; defaults to "VND" in the entity.
 * @param startDate     contract start date (required).
 * @param endDate       contract end date; omit for open-ended contracts.
 * @param notes         free-text notes.
 */
public record CreateContractRequest(
        @NotNull UUID contractorId,
        @NotBlank String title,
        String scope,
        BigDecimal contractValue,
        String currency,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String notes
) {}
