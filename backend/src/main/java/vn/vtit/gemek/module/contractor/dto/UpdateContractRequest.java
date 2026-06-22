/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.dto;

import vn.vtit.gemek.module.contractor.entity.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for updating an existing contract.
 *
 * <p>All fields are optional; only non-null fields will be applied by the service layer.
 *
 * @param title         updated title.
 * @param scope         updated scope of work.
 * @param contractValue updated monetary value.
 * @param endDate       updated end date.
 * @param status        updated lifecycle status.
 * @param notes         updated notes.
 */
public record UpdateContractRequest(
        String title,
        String scope,
        BigDecimal contractValue,
        LocalDate endDate,
        ContractStatus status,
        String notes
) {}
