/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vtit.gemek.module.vehicle.dto.VehicleResponse;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;

/**
 * MapStruct mapper for converting {@link Vehicle} entities to {@link VehicleResponse} DTOs.
 */
@Mapper(componentModel = "spring")
public interface VehicleMapper {

    /**
     * Maps a {@link Vehicle} entity to a {@link VehicleResponse}.
     *
     * @param vehicle the vehicle entity with resident and apartment associations loaded.
     * @return the populated response DTO.
     */
    @Mapping(target = "resident.id",            source = "resident.id")
    @Mapping(target = "resident.user.fullName",  source = "resident.user.fullName")
    @Mapping(target = "apartment.id",            source = "apartment.id")
    @Mapping(target = "apartment.unitNumber",    source = "apartment.unitNumber")
    @Mapping(target = "isActive",               source = "active")
    VehicleResponse toResponse(Vehicle vehicle);
}
