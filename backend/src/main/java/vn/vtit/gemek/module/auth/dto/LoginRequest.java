/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/login}.
 *
 * @param email    the user's registered email address.
 * @param password the user's plaintext password.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        String email,

        @NotBlank(message = "Password is required.")
        String password
) {}
