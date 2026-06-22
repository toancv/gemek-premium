/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import vn.vtit.gemek.module.contractor.entity.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only response DTO for a {@link vn.vtit.gemek.module.contractor.entity.Contract}.
 *
 * @param id            the contract UUID.
 * @param contractor    slim contractor reference (id + companyName).
 * @param title         short descriptive title.
 * @param scope         detailed scope of work.
 * @param contractValue total monetary value.
 * @param currency      ISO 4217 currency code.
 * @param startDate     contract start date.
 * @param endDate       contract end date; {@code null} for open-ended contracts.
 * @param status        current lifecycle status.
 * @param attachmentUrl MinIO object key for the signed document; {@code null} if absent.
 * @param notes         free-text notes.
 * @param createdBy     slim user reference for the creator; {@code null} if deleted.
 * @param createdAt     record creation timestamp.
 * @param updatedAt     record last-modified timestamp.
 */
public record ContractResponse(
        UUID id,
        ContractorRef contractor,
        String title,
        String scope,
        BigDecimal contractValue,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        ContractStatus status,
        String attachmentUrl,
        String notes,
        UserRef createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * Slim contractor reference embedded in contract responses.
     *
     * @param id          the contractor UUID.
     * @param companyName the company name.
     */
    public record ContractorRef(UUID id, String companyName) {}

    /**
     * Slim user reference embedded in contract responses.
     *
     * @param id       the user UUID.
     * @param fullName the user's full name.
     */
    public record UserRef(UUID id, String fullName) {}
}
