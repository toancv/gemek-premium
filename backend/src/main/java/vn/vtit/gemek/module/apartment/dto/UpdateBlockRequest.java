/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/blocks/{id}} (ADMIN only).
 *
 * @param name        new block name, required.
 * @param description optional description.
 */
public record UpdateBlockRequest(

        @NotBlank(message = "Block name is required.")
        @Size(max = 100, message = "Block name must not exceed 100 characters.")
        String name,

        String description
) {}
