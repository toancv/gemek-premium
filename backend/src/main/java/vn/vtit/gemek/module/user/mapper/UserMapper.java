/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;
import vn.vtit.gemek.module.user.dto.UserResponse;
import vn.vtit.gemek.module.user.entity.User;

/**
 * MapStruct mapper for converting {@link User} entities to response DTOs.
 *
 * <p>Spring component model is set globally via the compiler arg
 * {@code -Amapstruct.defaultComponentModel=spring}, so this mapper is
 * automatically a Spring bean.
 */
@Mapper
public interface UserMapper {

    /**
     * Maps a {@link User} entity to a summary {@link UserResponse}.
     *
     * @param user the user entity.
     * @return the summary response DTO.
     */
    @Mapping(target = "isActive", source = "active")
    UserResponse toUserResponse(User user);

    /**
     * Maps a {@link User} entity to a detailed {@link UserDetailResponse}.
     *
     * @param user the user entity.
     * @return the detail response DTO.
     */
    @Mapping(target = "isActive", source = "active")
    UserDetailResponse toUserDetailResponse(User user);
}
