/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.dto;

import vn.vtit.gemek.module.apartment.entity.ApartmentStatus;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.vehicle.entity.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full apartment detail response returned by GET /api/apartments/{id}.
 *
 * <p>Includes the list of currently active residents and all registered vehicles.
 *
 * @param id        the apartment UUID.
 * @param block     the block reference.
 * @param floor     floor number.
 * @param unitNumber unit number within the block.
 * @param areaSqm   floor area in square metres, or {@code null}.
 * @param status    current occupancy status.
 * @param notes     optional notes, or {@code null}.
 * @param residents list of currently active residents.
 * @param vehicles  list of registered vehicles for this apartment.
 */
public record ApartmentDetailResponse(
        UUID id,
        BlockRef block,
        Short floor,
        String unitNumber,
        BigDecimal areaSqm,
        ApartmentStatus status,
        String notes,
        List<ResidentRef> residents,
        List<VehicleRef> vehicles
) {

    /**
     * Minimal block reference embedded in the apartment detail.
     *
     * @param id   the block UUID.
     * @param name the block display name.
     */
    public record BlockRef(UUID id, String name) {}

    /**
     * Resident entry embedded in the apartment detail.
     *
     * @param id               the resident UUID.
     * @param user             the linked user's key fields.
     * @param type             OWNER or TENANT.
     * @param moveInDate       the date the resident moved in.
     * @param moveOutDate      the date the resident moved out, or {@code null} if active.
     * @param isPrimaryContact whether this resident is the primary contact.
     */
    public record ResidentRef(
            UUID id,
            UserRef user,
            ResidentType type,
            LocalDate moveInDate,
            LocalDate moveOutDate,
            boolean isPrimaryContact
    ) {}

    /**
     * User fields embedded in a resident entry.
     *
     * @param id       the user UUID.
     * @param fullName the user's display name.
     * @param phone    the user's phone number, or {@code null}.
     * @param email    the user's email address.
     */
    public record UserRef(UUID id, String fullName, String phone, String email) {}

    /**
     * Vehicle entry embedded in the apartment detail.
     *
     * @param id           the vehicle UUID.
     * @param licensePlate the vehicle's license plate number.
     * @param type         the vehicle type.
     * @param brand        the vehicle brand, or {@code null}.
     * @param color        the vehicle color, or {@code null}.
     * @param isActive     whether the vehicle registration is active.
     */
    public record VehicleRef(
            UUID id,
            String licensePlate,
            VehicleType type,
            String brand,
            String color,
            boolean isActive
    ) {}
}
