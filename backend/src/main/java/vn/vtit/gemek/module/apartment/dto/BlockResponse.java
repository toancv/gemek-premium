/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.dto;

import java.util.UUID;

/**
 * Response DTO for a {@code blocks} record.
 *
 * <p>Returned by GET /api/blocks, POST /api/blocks, and PUT /api/blocks/{id}.
 *
 * @param id          the block UUID.
 * @param name        the block display name.
 * @param description optional description, or {@code null}.
 */
public record BlockResponse(
        UUID id,
        String name,
        String description
) {}
