/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vtit.gemek.module.resident.dto.ResidentHistoryResponse;
import vn.vtit.gemek.module.resident.dto.ResidentResponse;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentHistory;

/**
 * MapStruct mapper for converting {@link Resident} and {@link ResidentHistory} entities
 * to their respective response DTOs.
 */
@Mapper(componentModel = "spring")
public interface ResidentMapper {

    /**
     * Maps a {@link Resident} entity to a {@link ResidentResponse}.
     *
     * @param resident the resident entity with user and apartment associations loaded.
     * @return the populated response DTO.
     */
    @Mapping(target = "user.id",           source = "user.id")
    @Mapping(target = "user.fullName",     source = "user.fullName")
    @Mapping(target = "user.email",        source = "user.email")
    @Mapping(target = "user.phone",        source = "user.phone")
    @Mapping(target = "user.dateOfBirth",  source = "user.dateOfBirth")
    @Mapping(target = "apartment.id",         source = "apartment.id")
    @Mapping(target = "apartment.unitNumber", source = "apartment.unitNumber")
    @Mapping(target = "apartment.block.name", source = "apartment.block.name")
    @Mapping(target = "isPrimaryContact",     source = "primaryContact")
    ResidentResponse toResponse(Resident resident);

    /**
     * Maps a {@link ResidentHistory} entity to a {@link ResidentHistoryResponse}.
     *
     * @param history the history entity with changedBy association loaded.
     * @return the populated history response DTO.
     */
    @Mapping(target = "changedBy.id",       source = "changedBy.id")
    @Mapping(target = "changedBy.fullName", source = "changedBy.fullName")
    ResidentHistoryResponse toHistoryResponse(ResidentHistory history);
}
