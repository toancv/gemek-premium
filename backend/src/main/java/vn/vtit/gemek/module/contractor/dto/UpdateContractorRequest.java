/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;

/**
 * Request body for updating an existing contractor.
 *
 * <p>All fields are optional; only non-null fields will be applied.
 *
 * @param companyName   updated company name.
 * @param contactPerson updated contact person.
 * @param phone         updated phone number.
 * @param email         updated email address.
 * @param address       updated physical address.
 * @param specialty     updated specialty.
 * @param taxCode       updated tax code.
 * @param notes         updated notes.
 */
public record UpdateContractorRequest(
        String companyName,
        String contactPerson,
        String phone,
        String email,
        String address,
        ContractorSpecialty specialty,
        String taxCode,
        String notes
) {}
