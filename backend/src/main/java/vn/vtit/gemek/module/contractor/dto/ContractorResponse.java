/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only response DTO for a {@link vn.vtit.gemek.module.contractor.entity.Contractor}.
 *
 * @param id            the contractor UUID.
 * @param companyName   legal company name.
 * @param contactPerson primary contact person name.
 * @param phone         contact phone number.
 * @param email         contact email address.
 * @param address       physical address.
 * @param specialty     area of specialisation.
 * @param taxCode       tax identification code.
 * @param rating        average rating (0–5); {@code null} if no ratings yet.
 * @param notes         free-text notes.
 * @param active        whether the contractor is available for assignment.
 * @param createdAt     record creation timestamp.
 */
public record ContractorResponse(
        UUID id,
        String companyName,
        String contactPerson,
        String phone,
        String email,
        String address,
        ContractorSpecialty specialty,
        String taxCode,
        BigDecimal rating,
        String notes,
        boolean active,
        OffsetDateTime createdAt
) {}
