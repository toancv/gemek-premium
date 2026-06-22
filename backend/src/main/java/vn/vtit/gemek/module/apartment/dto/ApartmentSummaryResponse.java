/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.dto;

import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;
import vn.vtit.gemek.module.resident.entity.ResidentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Summary apartment response returned in the paginated list endpoint.
 *
 * <p>Includes the primary contact resident when one exists.
 *
 * @param id             the apartment UUID.
 * @param block          the block reference (id and name only).
 * @param floor          floor number.
 * @param unitNumber     unit number within the block.
 * @param areaSqm        floor area in square metres, or {@code null}.
 * @param status         current occupancy status.
 * @param primaryContact the primary contact resident, or {@code null} if none.
 */
public record ApartmentSummaryResponse(
        UUID id,
        BlockRef block,
        Short floor,
        String unitNumber,
        BigDecimal areaSqm,
        ApartmentStatus status,
        PrimaryContactRef primaryContact
) {

    /**
     * Minimal block reference embedded in the apartment summary.
     *
     * @param id   the block UUID.
     * @param name the block display name.
     */
    public record BlockRef(UUID id, String name) {}

    /**
     * Minimal primary contact reference embedded in the apartment summary.
     *
     * @param id       the resident UUID.
     * @param fullName the resident's full name.
     * @param type     OWNER or TENANT.
     * @param phone    the resident's phone number, or {@code null}.
     */
    public record PrimaryContactRef(UUID id, String fullName, ResidentType type, String phone) {}
}
