/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.apartment.mapper;

import org.mapstruct.Mapper;
import vn.vtit.gemek.module.apartment.dto.BlockResponse;
import vn.vtit.gemek.module.apartment.entity.Block;

/**
 * MapStruct mapper for converting {@link Block} entities to response DTOs.
 *
 * <p>Spring component model is applied globally via the compiler argument
 * {@code -Amapstruct.defaultComponentModel=spring}.
 */
@Mapper
public interface BlockMapper {

    /**
     * Maps a {@link Block} entity to a {@link BlockResponse} DTO.
     *
     * @param block the block entity to map.
     * @return the response DTO.
     */
    BlockResponse toResponse(Block block);
}
