/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.vtit.gemek.module.user.entity.UserRole;

/**
 * Request body for {@code PUT /api/users/{id}} (ADMIN only).
 *
 * @param fullName display name.
 * @param phone    optional phone number.
 * @param role     the user's role.
 * @param isActive whether the account should be active.
 */
public record UpdateUserRequest(

        @NotBlank(message = "Full name is required.")
        @Size(max = 255, message = "Full name must not exceed 255 characters.")
        String fullName,

        @Size(max = 20, message = "Phone must not exceed 20 characters.")
        String phone,

        @NotNull(message = "Role is required.")
        UserRole role,

        @NotNull(message = "isActive is required.")
        Boolean isActive
) {}
