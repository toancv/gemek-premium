/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/login}.
 *
 * @param phone    the user's registered phone number (any standard VN format accepted;
 *                 canonical normalization applied by the service via {@code PhoneUtils.normalize()}).
 * @param password the user's plaintext password.
 */
public record LoginRequest(

        @NotBlank(message = "Phone number is required.")
        String phone,

        @NotBlank(message = "Password is required.")
        String password
) {}
