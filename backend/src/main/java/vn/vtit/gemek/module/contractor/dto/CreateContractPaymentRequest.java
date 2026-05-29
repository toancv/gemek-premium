/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for recording a payment against a contract.
 *
 * @param amount          payment amount; must be positive (required).
 * @param paymentDate     date the payment was made (required).
 * @param description     optional description of what the payment covers.
 * @param referenceNumber optional external reference (bank transfer ID, cheque number, etc.).
 */
public record CreateContractPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paymentDate,
        String description,
        String referenceNumber
) {}
