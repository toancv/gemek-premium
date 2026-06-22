/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only response DTO for a {@link vn.vtit.gemek.module.contractor.entity.ContractPayment}.
 *
 * @param id              the payment UUID.
 * @param contract        slim contract reference (id + title).
 * @param amount          payment amount.
 * @param paymentDate     calendar date the payment was made.
 * @param description     what the payment covers.
 * @param referenceNumber external reference (bank ID, cheque, etc.).
 * @param recordedBy      slim user reference for the recorder; {@code null} if deleted.
 * @param createdAt       record creation timestamp.
 */
public record ContractPaymentResponse(
        UUID id,
        ContractRef contract,
        BigDecimal amount,
        LocalDate paymentDate,
        String description,
        String referenceNumber,
        UserRef recordedBy,
        OffsetDateTime createdAt
) {

    /**
     * Slim contract reference embedded in payment responses.
     *
     * @param id    the contract UUID.
     * @param title the contract title.
     */
    public record ContractRef(UUID id, String title) {}

    /**
     * Slim user reference embedded in payment responses.
     *
     * @param id       the user UUID.
     * @param fullName the user's full name.
     */
    public record UserRef(UUID id, String fullName) {}
}
