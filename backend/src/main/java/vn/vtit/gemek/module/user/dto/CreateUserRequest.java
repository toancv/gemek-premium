/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.vtit.gemek.module.user.entity.UserRole;

/**
 * Request body for {@code POST /api/users} (ADMIN only).
 *
 * @param email    unique email address for the new user.
 * @param fullName display name.
 * @param phone    optional phone number.
 * @param role     the user's role in the system.
 * @param password initial password (min 8 chars, upper + lower + digit + special).
 */
public record CreateUserRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email,

        @NotBlank(message = "Full name is required.")
        @Size(max = 255, message = "Full name must not exceed 255 characters.")
        String fullName,

        @Size(max = 20, message = "Phone must not exceed 20 characters.")
        String phone,

        @NotNull(message = "Role is required.")
        UserRole role,

        @NotBlank(message = "Password is required.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
                message = "Password must be at least 8 characters and include upper, lower, digit, and special character."
        )
        String password
) {}
