/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code PUT /api/users/{id}/reset-password} (ADMIN only).
 *
 * @param newPassword the new password to set (min 8 chars, upper + lower + digit + special).
 */
public record ResetPasswordRequest(

        @NotBlank(message = "New password is required.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
                message = "Password must be at least 8 characters and include upper, lower, digit, and special character."
        )
        String newPassword
) {}
