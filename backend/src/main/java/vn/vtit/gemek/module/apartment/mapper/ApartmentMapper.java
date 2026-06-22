/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vtit.gemek.module.apartment.dto.ApartmentDetailResponse;
import vn.vtit.gemek.module.apartment.dto.ApartmentSummaryResponse;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;

/**
 * MapStruct mapper for converting {@link Apartment}, {@link Resident}, and
 * {@link Vehicle} entities to apartment response DTOs.
 *
 * <p>Spring component model is applied globally via the compiler argument
 * {@code -Amapstruct.defaultComponentModel=spring}.
 */
@Mapper
public interface ApartmentMapper {

    /**
     * Maps an {@link Apartment} entity to an {@link ApartmentSummaryResponse}.
     *
     * <p>The {@code primaryContact} field is not derived from the entity graph and
     * must be set separately by the service layer after calling this method.
     *
     * @param apartment the apartment entity (with block eagerly loaded).
     * @return the summary response DTO with {@code primaryContact} set to {@code null}.
     */
    @Mapping(target = "block.id",   source = "block.id")
    @Mapping(target = "block.name", source = "block.name")
    @Mapping(target = "primaryContact", ignore = true)
    ApartmentSummaryResponse toSummaryResponse(Apartment apartment);

    /**
     * Maps an {@link Apartment} entity to the base fields of an {@link ApartmentDetailResponse}.
     *
     * <p>The {@code residents} and {@code vehicles} lists are not derived from the entity
     * and must be supplied separately by the service layer.
     *
     * @param apartment the apartment entity (with block eagerly loaded).
     * @return the detail response DTO with empty residents and vehicles lists.
     */
    @Mapping(target = "block.id",   source = "block.id")
    @Mapping(target = "block.name", source = "block.name")
    @Mapping(target = "residents",  ignore = true)
    @Mapping(target = "vehicles",   ignore = true)
    ApartmentDetailResponse toDetailResponse(Apartment apartment);

    /**
     * Maps a {@link Resident} entity to a {@link ApartmentDetailResponse.ResidentRef}.
     *
     * @param resident the resident entity (with user eagerly loaded).
     * @return the resident reference DTO.
     */
    @Mapping(target = "user.id",       source = "user.id")
    @Mapping(target = "user.fullName", source = "user.fullName")
    @Mapping(target = "user.phone",    source = "user.phone")
    @Mapping(target = "user.email",    source = "user.email")
    @Mapping(target = "isPrimaryContact", source = "primaryContact")
    ApartmentDetailResponse.ResidentRef toResidentRef(Resident resident);

    /**
     * Maps a {@link Vehicle} entity to a {@link ApartmentDetailResponse.VehicleRef}.
     *
     * @param vehicle the vehicle entity.
     * @return the vehicle reference DTO.
     */
    @Mapping(target = "isActive", source = "active")
    ApartmentDetailResponse.VehicleRef toVehicleRef(Vehicle vehicle);
}
