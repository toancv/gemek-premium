/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/auth/me/password}.
 *
 * @param currentPassword the caller's current password for verification.
 * @param newPassword     the new password (min 8 chars, upper + lower + digit + special).
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required.")
        String currentPassword,

        @NotBlank(message = "New password is required.")
        String newPassword
) {}
