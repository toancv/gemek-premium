/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;

/**
 * Request body for creating a new contractor.
 *
 * @param companyName   legal company name (required).
 * @param contactPerson primary contact person name.
 * @param phone         contact phone number.
 * @param email         contact email address.
 * @param address       physical address.
 * @param specialty     area of specialisation (required).
 * @param taxCode       tax identification code.
 * @param notes         free-text notes.
 */
public record CreateContractorRequest(
        @NotBlank String companyName,
        String contactPerson,
        String phone,
        String email,
        String address,
        @NotNull ContractorSpecialty specialty,
        String taxCode,
        String notes
) {}
